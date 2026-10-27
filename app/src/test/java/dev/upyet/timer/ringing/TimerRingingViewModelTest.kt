package dev.upyet.timer.ringing

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.camera.EvidenceRecorder
import dev.upyet.evidence.camera.EvidenceRecordingState
import dev.upyet.evidence.camera.EvidenceRequest
import dev.upyet.evidence.domain.EvidenceSink
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.testing.FakeUserUnlockState
import dev.upyet.testing.FixedTimeProvider
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.playback.RingingTimer
import dev.upyet.timer.playback.TimerRingingRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimerRingingViewModelTest {
    private val now = Instant.parse("2026-08-24T10:00:00Z")
    private val events = mutableListOf<String>()
    private val registry = TimerRingingRegistry()
    private val commands = FakeCommands(events)
    private val recorder = FakeRecorder(events)
    private val occurrences = FakeOccurrenceRepository()

    // Process-scoped in the app (AGENTS.md §2 invariant 11): one showing outlives any one screen, so the tests share it too.
    private val showing = TimerShowingOccurrence(
        Lazy<OccurrenceRepository> { occurrences },
        FakeUserUnlockState(unlocked = true),
        FixedTimeProvider(now),
        registry,
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun stopSendsTheCommandForThatTimer() = runTest {
        registry.add(ringing(1))
        val viewModel = viewModel()

        viewModel.stop(TimerId(1))

        assertThat(events).containsExactly("command:stop:1")
    }

    @Test fun addMinuteAndStopAllSendTheirCommands() = runTest {
        registry.add(ringing(1))
        registry.add(ringing(2))
        val viewModel = viewModel()

        viewModel.addMinute(TimerId(2))
        viewModel.stopAll()

        assertThat(events).containsExactly("command:addMinute:2", "command:stopAll").inOrder()
    }

    /** Invariant 13: recording stops with the last recording-on timer, even while a recording-off one still rings. */
    @Test fun recordingStopsWhenTheLastRecordingOnTimerLeavesTheShowing() = runTest {
        registry.add(ringing(1, evidence = true))
        registry.add(ringing(2, evidence = false))
        viewModel().recording()

        registry.remove(TimerId(1))

        assertThat(events).containsExactly("recorder:stop")
    }

    @Test fun recordingKeepsRunningWhileAnotherRecordingOnTimerStillRings() = runTest {
        registry.add(ringing(1, evidence = true))
        registry.add(ringing(2, evidence = true))
        viewModel().recording()

        registry.remove(TimerId(1))

        assertThat(events).isEmpty()
    }

    /**
     * The coordinator is shared with the alarm path: a timer screen whose showing never recorded must never stop the
     * camera, or it would cut a ringing alarm's evidence short.
     */
    @Test fun aShowingThatNeverRecordedNeverStopsTheRecorder() = runTest {
        registry.add(ringing(1, evidence = false))
        viewModel()

        registry.remove(TimerId(1))

        assertThat(events).isEmpty()
    }

    @Test fun focusingAChipMovesTheFocusAndSurvivesInTheSavedState() = runTest {
        registry.add(ringing(1))
        registry.add(ringing(2))
        val saved = SavedStateHandle()
        val viewModel = viewModel(saved)
        collect(viewModel)
        assertThat(viewModel.state.value.focused?.id).isEqualTo(TimerId(2))

        viewModel.focus(TimerId(1))

        assertThat(viewModel.state.value.focused?.id).isEqualTo(TimerId(1))
        assertThat(viewModel(saved).also { collect(it) }.state.value.focused?.id).isEqualTo(TimerId(1))
    }

    @Test fun theShowingEndsOnlyAfterItWasSeen() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        assertThat(viewModel.state.value.phase).isEqualTo(TimerRingingPhase.LOADING)

        registry.add(ringing(1))
        assertThat(viewModel.state.value.phase).isEqualTo(TimerRingingPhase.RINGING)

        registry.remove(TimerId(1))
        assertThat(viewModel.state.value.phase).isEqualTo(TimerRingingPhase.ENDED)
    }

    @Test fun theEvidenceRequestIsReadLiveFromTheCurrentShowing() = runTest {
        registry.add(ringing(1, evidence = true))
        val viewModel = viewModel()
        collect(viewModel)
        assertThat(viewModel.currentEvidenceRequest()?.evidenceEnabled).isTrue()

        registry.remove(TimerId(1))

        assertThat(viewModel.currentEvidenceRequest()).isNull()
    }

    /** One showing that records writes exactly one timer occurrence, and the evidence request carries its id. */
    @Test fun aRecordingOnShowingCreatesOneOccurrenceAndTheRequestCarriesItsId() = runTest {
        registry.add(ringing(1, evidence = true))
        registry.add(ringing(2, evidence = false))
        val viewModel = viewModel()
        collect(viewModel)

        registry.add(ringing(3, evidence = true))

        assertThat(occurrences.created.map { it.id }).containsExactly(OccurrenceId(1))
        assertThat(viewModel.currentEvidenceRequest()).isEqualTo(EvidenceRequest(OccurrenceId(1), evidenceEnabled = true))
    }

    /** The occurrence spans the showing: it completes as STOPPED only when the last ringing timer leaves. */
    @Test fun theOccurrenceCompletesAsStoppedWhenTheShowingEmpties() = runTest {
        registry.add(ringing(1, evidence = true))
        val viewModel = viewModel()
        collect(viewModel)

        registry.remove(TimerId(1))

        assertThat(
            occurrences.completed,
        ).containsExactly(FakeOccurrenceRepository.Completed(OccurrenceId(1), now, OccurrenceOutcome.STOPPED))
        assertThat(viewModel.currentEvidenceRequest()).isNull()
    }

    /** Starts the evidence session the way a resumed ringing screen does, then forgets that start. */
    private fun TimerRingingViewModel.recording() {
        evidence.onVisible(TestHost(), currentEvidenceRequest())
        events.clear()
    }

    private class TestHost : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    private fun TestScope.collect(viewModel: TimerRingingViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = TimerRingingViewModel(
        registry = registry,
        commands = commands,
        timeProvider = FixedTimeProvider(now),
        savedStateHandle = saved,
        evidenceRecorder = recorder,
        occurrenceRepository = Lazy<OccurrenceRepository> { occurrences },
        showing = showing,
    )

    private fun ringing(id: Long, evidence: Boolean = false) = RingingTimer(
        TimerId(id),
        label = "Timer $id",
        length = Duration.ofMinutes(5),
        ringtoneUri = null,
        evidenceEnabled = evidence,
        ringingSince = now.minusSeconds(100 - id),
    )

    private class FakeCommands(private val events: MutableList<String>) : TimerRingingCommands {
        override fun stop(id: TimerId) {
            events += "command:stop:${id.value}"
        }

        override fun addMinute(id: TimerId) {
            events += "command:addMinute:${id.value}"
        }

        override fun stopAll() {
            events += "command:stopAll"
        }
    }

    private class FakeRecorder(private val events: MutableList<String>) : EvidenceRecorder {
        override val state: StateFlow<EvidenceRecordingState> = MutableStateFlow(EvidenceRecordingState.Idle)
        override val surfaceRequest: StateFlow<SurfaceRequest?> = MutableStateFlow(null)

        override suspend fun start(host: LifecycleOwner, occurrenceId: OccurrenceId?, evidenceEnabled: Boolean, sink: EvidenceSink) {
            events += "recorder:start"
        }

        override fun stop() {
            events += "recorder:stop"
        }

        override fun release() {
            events += "recorder:release"
        }
    }
}
