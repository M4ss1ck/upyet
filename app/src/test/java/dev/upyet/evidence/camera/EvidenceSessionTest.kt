package dev.upyet.evidence.camera

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceSink
import dev.upyet.testing.FixedTimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import java.time.Instant

/**
 * Pins the evidence component contract that both ringing screens share (AGENTS.md §10, ADR 0010): when a
 * ringing screen becomes visible, recording starts and the occurrence is marked visible once; when it is hidden
 * the recording stops and the camera is released, while ending the ring only stops the recording; the request
 * is read at the moment the host resumes, not when it was bound.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EvidenceSessionTest {
    private val events = mutableListOf<String>()
    private val recorder = FakeRecorder(events)
    private val sink = FakeSink(events)
    private val timeProvider = FixedTimeProvider()

    // Unconfined, so every launched mark-and-start runs before the call that launched it returns.
    private val scope = TestScope(UnconfinedTestDispatcher())

    private val session = EvidenceSession(recorder, sink, scope, timeProvider)

    @Test fun nullRequestDoesNothing() {
        val host = TestHost()

        session.onVisible(host, null)

        assertThat(events).isEmpty()
        assertThat(sink.visible).isEmpty()
    }

    @Test fun visibleRequestStartsRecorderAndMarksOccurrenceVisible() {
        val host = TestHost()

        session.onVisible(host, EvidenceRequest(OccurrenceId(7), evidenceEnabled = true))

        assertThat(events).containsExactly("visible:7", "start:7:true")
        assertThat(sink.visible).containsExactly(OccurrenceId(7) to timeProvider.now())
        assertThat(recorder.starts).hasSize(1)
        assertThat(recorder.starts.single().host).isSameInstanceAs(host)
        assertThat(recorder.starts.single().sink).isSameInstanceAs(sink)
        assertThat(recorder.starts.single().occurrenceId).isEqualTo(OccurrenceId(7))
    }

    @Test fun occurrenceIsMarkedVisibleOnceButRecorderStartsEveryTime() {
        val host = TestHost()

        session.onVisible(host, EvidenceRequest(OccurrenceId(7), evidenceEnabled = true))
        session.onVisible(host, EvidenceRequest(OccurrenceId(7), evidenceEnabled = true))

        assertThat(sink.visible).containsExactly(OccurrenceId(7) to timeProvider.now())
        assertThat(events.count { it == "visible:7" }).isEqualTo(1)
        assertThat(events.count { it == "start:7:true" }).isEqualTo(2)
    }

    @Test fun differentOccurrenceIsMarkedVisibleToo() {
        val host = TestHost()

        session.onVisible(host, EvidenceRequest(OccurrenceId(7), evidenceEnabled = true))
        session.onVisible(host, EvidenceRequest(OccurrenceId(8), evidenceEnabled = true))

        assertThat(sink.visible.map { it.first }).containsExactly(OccurrenceId(7), OccurrenceId(8)).inOrder()
        assertThat(events).containsAtLeast("visible:8", "start:8:true")
    }

    @Test fun nullOccurrenceStartsRecorderWithoutMarkingAnything() {
        val host = TestHost()

        session.onVisible(host, EvidenceRequest(occurrenceId = null, evidenceEnabled = false))

        assertThat(events).containsExactly("start:null:false")
        assertThat(sink.visible).isEmpty()
        assertThat(recorder.starts.single().occurrenceId).isNull()
        assertThat(recorder.starts.single().sink).isSameInstanceAs(sink)
    }

    @Test fun disabledEvidenceIsStillForwardedAndStillMarksVisible() {
        val host = TestHost()

        session.onVisible(host, EvidenceRequest(OccurrenceId(7), evidenceEnabled = false))

        assertThat(events).containsExactly("visible:7", "start:7:false")
        assertThat(recorder.starts.single().evidenceEnabled).isFalse()
        assertThat(sink.visible).containsExactly(OccurrenceId(7) to timeProvider.now())
    }

    @Test fun hidingStopsThenReleases() {
        session.onHidden()

        assertThat(events).containsExactly("stop", "release").inOrder()
    }

    @Test fun stopRecordingStopsWithoutRelease() {
        session.stopRecording()

        assertThat(events).containsExactly("stop")
    }

    @Test fun hostResumingStartsWithRequestEvaluatedAtResume() {
        val host = resumableHost()
        var current: EvidenceRequest? = EvidenceRequest(OccurrenceId(7), evidenceEnabled = true)
        session.bind(host) { current }

        current = EvidenceRequest(OccurrenceId(8), evidenceEnabled = false)
        host.lifecycle.currentState = Lifecycle.State.RESUMED

        assertThat(events).containsExactly("visible:8", "start:8:false")
        assertThat(recorder.starts.single().host).isSameInstanceAs(host)
        assertThat(recorder.starts.single().sink).isSameInstanceAs(sink)
    }

    @Test fun hostStoppingStopsThenReleases() {
        val host = resumableHost()
        session.bind(host) { EvidenceRequest(OccurrenceId(7), evidenceEnabled = true) }
        host.lifecycle.currentState = Lifecycle.State.RESUMED
        events.clear()

        host.lifecycle.currentState = Lifecycle.State.CREATED

        assertThat(events).containsExactly("stop", "release").inOrder()
    }

    @Test fun pauseAloneDoesNothing() {
        val host = resumableHost()
        session.bind(host) { EvidenceRequest(OccurrenceId(7), evidenceEnabled = true) }
        host.lifecycle.currentState = Lifecycle.State.RESUMED
        events.clear()

        host.lifecycle.currentState = Lifecycle.State.STARTED

        assertThat(events).isEmpty()
    }

    @Test fun resumingAfterStopStartsASecondSegmentWithoutMarkingAgain() {
        val host = resumableHost()
        session.bind(host) { EvidenceRequest(OccurrenceId(7), evidenceEnabled = true) }
        host.lifecycle.currentState = Lifecycle.State.RESUMED
        host.lifecycle.currentState = Lifecycle.State.CREATED
        host.lifecycle.currentState = Lifecycle.State.RESUMED

        assertThat(events.filter { it == "start:7:true" }).hasSize(2)
        assertThat(events.filter { it == "visible:7" }).hasSize(1)
        assertThat(events.filter { it == "stop" || it == "release" }).containsExactly("stop", "release").inOrder()
        assertThat(sink.visible).containsExactly(OccurrenceId(7) to timeProvider.now())
        assertThat(recorder.starts).hasSize(2)
    }

    @Test fun disposeRemovesObserverAndStopsThenReleasesOnce() {
        val host = resumableHost()
        val handle = session.bind(host) { EvidenceRequest(OccurrenceId(7), evidenceEnabled = true) }
        host.lifecycle.currentState = Lifecycle.State.RESUMED
        events.clear()

        handle.dispose()

        assertThat(events).containsExactly("stop", "release").inOrder()

        events.clear()
        host.lifecycle.currentState = Lifecycle.State.STARTED
        host.lifecycle.currentState = Lifecycle.State.RESUMED
        host.lifecycle.currentState = Lifecycle.State.CREATED

        assertThat(events).isEmpty()
    }

    @Test fun stateAndSurfaceRequestAreTheRecorderFlows() {
        assertThat(session.state).isSameInstanceAs(recorder.state)
        assertThat(session.surfaceRequest).isSameInstanceAs(recorder.surfaceRequest)
    }

    /** Starts at CREATED so that a test can drive it to RESUMED, or to CREATED to fire ON_STOP. */
    private fun resumableHost(): TestHost = TestHost().also { it.lifecycle.currentState = Lifecycle.State.CREATED }

    private class TestHost : LifecycleOwner {
        // createUnsafe skips the main-thread check, which does not exist on the JVM.
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    }

    private data class Start(
        val host: LifecycleOwner,
        val occurrenceId: OccurrenceId?,
        val evidenceEnabled: Boolean,
        val sink: EvidenceSink,
    )

    private class FakeRecorder(private val events: MutableList<String>) : EvidenceRecorder {
        override val state = MutableStateFlow<EvidenceRecordingState>(EvidenceRecordingState.Idle)
        override val surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
        val starts = mutableListOf<Start>()

        override suspend fun start(host: LifecycleOwner, occurrenceId: OccurrenceId?, evidenceEnabled: Boolean, sink: EvidenceSink) {
            starts += Start(host, occurrenceId, evidenceEnabled, sink)
            events += "start:${occurrenceId?.value}:$evidenceEnabled"
        }

        override fun stop() {
            events += "stop"
        }

        override fun release() {
            events += "release"
        }
    }

    private class FakeSink(private val events: MutableList<String>) : EvidenceSink {
        val visible = mutableListOf<Pair<OccurrenceId, Instant>>()

        override suspend fun markActivityVisible(id: OccurrenceId, at: Instant) {
            visible += id to at
            events += "visible:${id.value}"
        }

        override suspend fun insertSegment(segment: EvidenceSegment): Long = 1L

        override suspend fun updateSegment(segment: EvidenceSegment) = Unit
    }
}
