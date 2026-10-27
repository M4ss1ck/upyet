package dev.upyet.timer.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.upyet.core.time.TimerClock
import dev.upyet.settings.data.SettingsRepository
import dev.upyet.testing.FakeDataStore
import dev.upyet.testing.FakeTimerRepository
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import dev.upyet.timer.domain.TimerTransition
import dev.upyet.timer.ringing.TimerRingingCommands
import dev.upyet.timer.scheduling.TimerEditor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun recordSwitchFollowsGlobalFalseByDefault() = runTest {
        val h = Harness()
        val observer = launch { h.viewModel.state.collect {} }

        runCurrent()

        assertThat(h.viewModel.state.value.recordNewTimer).isFalse()
        observer.cancel()
    }

    @Test fun recordSwitchFollowsGlobalTrueWhenSet() = runTest {
        val h = Harness()
        h.settings.setTimerEvidenceByDefault(true)
        val observer = launch { h.viewModel.state.collect {} }

        runCurrent()

        assertThat(h.viewModel.state.value.recordNewTimer).isTrue()
        observer.cancel()
    }

    @Test fun explicitSwitchOverridesGlobalInBothDirections() = runTest {
        val h = Harness()
        val observer = launch { h.viewModel.state.collect {} }

        h.settings.setTimerEvidenceByDefault(true)
        h.viewModel.onRecordNewTimer(false)
        runCurrent()
        assertThat(h.viewModel.state.value.recordNewTimer).isFalse()

        h.settings.setTimerEvidenceByDefault(false)
        h.viewModel.onRecordNewTimer(true)
        runCurrent()
        assertThat(h.viewModel.state.value.recordNewTimer).isTrue()
        observer.cancel()
    }

    @Test fun newTimerTakesGlobalValueAtCreationAndLaterChangesDoNotReachIt() = runTest {
        val h = Harness()
        h.settings.setTimerEvidenceByDefault(true)
        h.viewModel.onMinutes(5)

        h.viewModel.onStart()
        runCurrent()
        h.settings.setTimerEvidenceByDefault(false)
        runCurrent()

        assertThat(h.repository.all.single().evidenceEnabled).isTrue()
    }

    @Test fun explicitSwitchWinsOverGlobalAtCreation() = runTest {
        val h = Harness()
        h.settings.setTimerEvidenceByDefault(true)
        h.viewModel.onRecordNewTimer(false)
        h.viewModel.onMinutes(5)

        h.viewModel.onStart()
        runCurrent()

        assertThat(h.repository.all.single().evidenceEnabled).isFalse()
    }

    @Test fun startCreatesRunningTimerOfWheelLengthAndClearsLabelButKeepsWheels() = runTest {
        val h = Harness()
        val observer = launch { h.viewModel.state.collect {} }
        h.viewModel.onHours(1)
        h.viewModel.onMinutes(5)
        h.viewModel.onSeconds(7)
        h.viewModel.onLabel("  Tea  ")

        h.viewModel.onStart()
        runCurrent()

        val timer = h.repository.all.single()
        assertThat(timer.runState).isEqualTo(TimerRunState.Running(Duration.ofSeconds(3_907), h.clock.anchor))
        assertThat(timer.length).isEqualTo(Duration.ofSeconds(3_907))
        assertThat(timer.label).isEqualTo("Tea")
        assertThat(h.viewModel.state.value.label).isEmpty()
        assertThat(h.viewModel.state.value.hours).isEqualTo(1)
        assertThat(h.viewModel.state.value.minutes).isEqualTo(5)
        observer.cancel()
    }

    @Test fun aSecondStartWhileTheFirstIsStillWritingAddsNothing() = runTest {
        val h = Harness()
        h.viewModel.onMinutes(5)
        val gate = CompletableDeferred<Unit>()
        h.editor.addGate = gate

        h.viewModel.onStart()
        h.viewModel.onStart()
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertThat(h.repository.all).hasSize(1)
    }

    @Test fun startAtZeroAddsNothing() = runTest {
        val h = Harness()

        h.viewModel.onStart()
        runCurrent()

        assertThat(h.repository.all).isEmpty()
        assertThat(h.repository.upserts).isEqualTo(0)
    }

    @Test fun presetNinetyBecomesOneHourThirtyMinutes() = runTest {
        val h = Harness()
        val observer = launch { h.viewModel.state.collect {} }

        h.viewModel.onPreset(90)
        runCurrent()

        with(h.viewModel.state.value) {
            assertThat(hours).isEqualTo(1)
            assertThat(minutes).isEqualTo(30)
            assertThat(seconds).isEqualTo(0)
        }
        observer.cancel()
    }

    @Test fun wheelValuesAreCoercedToTheirRanges() = runTest {
        val h = Harness()
        val observer = launch { h.viewModel.state.collect {} }

        h.viewModel.onHours(99)
        h.viewModel.onMinutes(-4)
        h.viewModel.onSeconds(75)
        runCurrent()

        with(h.viewModel.state.value) {
            assertThat(hours).isEqualTo(23)
            assertThat(minutes).isEqualTo(0)
            assertThat(seconds).isEqualTo(59)
        }
        observer.cancel()
    }

    @Test fun ringingAddMinuteAndStopGoToRingingCommands() = runTest {
        val h = Harness(listOf(timer(1, TimerRunState.Ringing)))

        h.viewModel.onAddMinute(TimerId(1))
        h.viewModel.onStop(TimerId(1))
        runCurrent()

        assertThat(h.ringing.addMinutes).containsExactly(TimerId(1))
        assertThat(h.ringing.stops).containsExactly(TimerId(1))
        assertThat(h.editor.commands).isEmpty()
    }

    @Test fun runningAddMinuteGoesToEditorAndAddsAMinute() = runTest {
        val h = Harness(listOf(timer(1, TimerRunState.Running(Duration.ofMinutes(5), h0))))

        h.viewModel.onAddMinute(TimerId(1))
        runCurrent()

        assertThat(h.editor.commands).containsExactly("add_minute")
        assertThat(h.ringing.addMinutes).isEmpty()
        assertThat(h.repository.all.single().runState).isEqualTo(TimerRunState.Running(Duration.ofMinutes(6), h0))
    }

    @Test fun evidenceToggleOnRingingTimerIsRefusedAndUnchanged() = runTest {
        val h = Harness(listOf(timer(1, TimerRunState.Ringing)))

        h.viewModel.onEvidence(TimerId(1), true)
        runCurrent()

        val timer = h.repository.all.single()
        assertThat(timer.evidenceEnabled).isFalse()
        assertThat(timer.runState).isEqualTo(TimerRunState.Ringing)
    }

    @Test fun evidenceToggleOnIdleTimerApplies() = runTest {
        val h = Harness(listOf(timer(1, TimerRunState.Idle)))

        h.viewModel.onEvidence(TimerId(1), true)
        runCurrent()

        assertThat(h.repository.all.single().evidenceEnabled).isTrue()
    }

    @Test fun pauseResumeResetAndDeleteApplyTheTransitions() = runTest {
        val h = Harness(listOf(timer(1, TimerRunState.Running(Duration.ofMinutes(5), h0))))

        h.viewModel.onPause(TimerId(1))
        runCurrent()
        assertThat(h.repository.all.single().runState).isEqualTo(TimerRunState.Paused(Duration.ofMinutes(5)))

        h.viewModel.onResume(TimerId(1))
        runCurrent()
        assertThat(h.repository.all.single().runState).isEqualTo(TimerRunState.Running(Duration.ofMinutes(5), h0))

        h.viewModel.onReset(TimerId(1))
        runCurrent()
        assertThat(h.repository.all.single().runState).isEqualTo(TimerRunState.Idle)

        h.viewModel.onDelete(TimerId(1))
        runCurrent()
        assertThat(h.repository.all).isEmpty()
        assertThat(h.editor.commands).containsExactly("pause", "start", "reset", "delete").inOrder()
    }

    @Test fun countdownRefreshesWhileATimerRuns() = runTest {
        val h = Harness(listOf(timer(1, TimerRunState.Running(Duration.ofMinutes(10), h0))))
        val observer = launch { h.viewModel.state.collect {} }
        runCurrent()
        assertThat(h.viewModel.state.value.timers.single().remaining).isEqualTo(Duration.ofMinutes(10))

        h.clock.anchor = at(2_500)
        testScheduler.advanceTimeBy(1_000)
        runCurrent()

        assertThat(h.viewModel.state.value.timers.single().remaining).isEqualTo(Duration.ofSeconds(598))
        observer.cancel()
    }

    private val h0 = TimerAnchor(Duration.ofSeconds(1_000), Instant.parse("2026-10-26T10:00:00Z"))

    private fun at(ms: Long) = TimerAnchor(h0.elapsedRealtime.plusMillis(ms), h0.wallClock.plusMillis(ms))

    private fun timer(id: Long, state: TimerRunState) =
        Timer.restore(TimerId(id), Duration.ofMinutes(5), "", null, false, state, h0.wallClock)

    private class MutableClock(var anchor: TimerAnchor) : TimerClock {
        override fun now(): TimerAnchor = anchor
    }

    /** Applies each command to the fake repository the way the real editor does, refusing Ringing timers. */
    private class FakeTimerEditor(private val repository: FakeTimerRepository, private val clock: TimerClock) : TimerEditor {
        val commands = mutableListOf<String>()

        /** When set, [add] suspends until it completes: a slow database write. */
        var addGate: CompletableDeferred<Unit>? = null

        override suspend fun add(timer: Timer): TimerId {
            addGate?.await()
            val id = TimerId((repository.all.maxOfOrNull { it.id.value } ?: 0L) + 1)
            repository.upsert(
                Timer.restore(id, timer.length, timer.label, timer.ringtoneUri, timer.evidenceEnabled, timer.runState, timer.createdAt),
            )
            return id
        }

        override suspend fun edit(
            id: TimerId,
            command: String,
            onlyIf: (Timer) -> Boolean,
            transition: (Timer, TimerAnchor) -> TimerTransition,
        ): TimerTransition {
            commands += command
            val timer = repository.getTimer(id) ?: return TimerTransition.NoOp
            if (timer.runState == TimerRunState.Ringing || !onlyIf(timer)) return TimerTransition.NoOp
            return when (val result = transition(timer, clock.now())) {
                is TimerTransition.Applied -> {
                    repository.upsert(result.timer)
                    result
                }

                TimerTransition.Removed -> {
                    repository.delete(id)
                    result
                }

                TimerTransition.NoOp, TimerTransition.NotOffered -> result
            }
        }
    }

    private class FakeRingingCommands : TimerRingingCommands {
        val stops = mutableListOf<TimerId>()
        val addMinutes = mutableListOf<TimerId>()

        override fun stop(id: TimerId) {
            stops += id
        }

        override fun addMinute(id: TimerId) {
            addMinutes += id
        }

        override fun stopAll() {}
    }

    private inner class Harness(initial: List<Timer> = emptyList()) {
        val clock = MutableClock(h0)
        val repository = FakeTimerRepository(initial)
        val editor = FakeTimerEditor(repository, clock)
        val ringing = FakeRingingCommands()
        val settings = SettingsRepository(FakeDataStore())
        val viewModel = TimerViewModel(SavedStateHandle(), repository, editor, settings, clock, ringing)
    }
}
