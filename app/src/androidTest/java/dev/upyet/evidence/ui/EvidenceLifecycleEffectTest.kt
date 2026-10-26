package dev.upyet.evidence.ui

import androidx.camera.core.SurfaceRequest
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.core.time.TimeProvider
import dev.upyet.evidence.camera.EvidenceRecorder
import dev.upyet.evidence.camera.EvidenceRecordingState
import dev.upyet.evidence.camera.EvidenceRequest
import dev.upyet.evidence.camera.EvidenceSession
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/**
 * The Compose half of the evidence component: what a ringing screen's composition asks the session for, and
 * when. Driven through a host lifecycle the test controls rather than a real Activity, which is the point:
 * the component accepts any host (AGENTS.md §10).
 */
@RunWith(AndroidJUnit4::class)
class EvidenceLifecycleEffectTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val host = TestHost()
    private val session = EvidenceSession(RecordingRecorder(events), NoSink, CoroutineScope(Dispatchers.Unconfined), FixedTime)
    private var live: EvidenceRequest? = EvidenceRequest(OccurrenceId(7), evidenceEnabled = true)
    private var ringing by mutableStateOf(false)

    @Test fun startingToRingWhileUpRequestsRecording() {
        showResumed()
        assertThat(events).isEmpty()

        compose.runOnIdle { ringing = true }

        compose.runOnIdle { assertThat(events).containsExactly("start:7") }
    }

    @Test fun stoppingReleasesAndResumingOpensANewSegment() {
        showResumed()
        compose.runOnIdle { ringing = true }
        compose.runOnIdle { events.clear() }

        compose.runOnIdle { host.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { host.lifecycle.currentState = Lifecycle.State.RESUMED }

        compose.runOnIdle { assertThat(events).containsExactly("stop", "release", "start:7").inOrder() }
    }

    /** Composition still says "ringing" while stopped; the ring ended meanwhile, so the resume must record nothing. */
    @Test fun resumeReadsTheLiveRequestNotTheComposedOne() {
        showResumed()
        compose.runOnIdle { ringing = true }
        compose.runOnIdle { host.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { events.clear() }

        live = null
        compose.runOnIdle { host.lifecycle.currentState = Lifecycle.State.RESUMED }

        compose.runOnIdle { assertThat(events).isEmpty() }
    }

    @Test fun leavingCompositionReleasesTheCamera() {
        var shown by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides host) {
                if (shown) EvidenceLifecycleEffect(session, ringing = false, currentRequest = { live })
            }
        }
        compose.runOnIdle { events.clear() }

        compose.runOnIdle { shown = false }

        compose.runOnIdle { assertThat(events).containsExactly("stop", "release").inOrder() }
    }

    private fun showResumed() {
        compose.runOnIdle { host.lifecycle.currentState = Lifecycle.State.RESUMED }
        // While not ringing, the catch-up ON_RESUME reads a request but the screen has not published one yet.
        live = null
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides host) {
                EvidenceLifecycleEffect(session, ringing = ringing, currentRequest = { live })
            }
        }
        compose.runOnIdle { live = EvidenceRequest(OccurrenceId(7), evidenceEnabled = true) }
    }

    private class TestHost : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    }

    private class RecordingRecorder(private val events: MutableList<String>) : EvidenceRecorder {
        override val state = MutableStateFlow<EvidenceRecordingState>(EvidenceRecordingState.Idle)
        override val surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)

        override suspend fun start(host: LifecycleOwner, occurrenceId: OccurrenceId?, evidenceEnabled: Boolean, sink: EvidenceSink) {
            events += "start:${occurrenceId?.value}"
        }

        override fun stop() {
            events += "stop"
        }

        override fun release() {
            events += "release"
        }
    }

    private object NoSink : EvidenceSink {
        override suspend fun markActivityVisible(id: OccurrenceId, at: Instant) = Unit

        override suspend fun insertSegment(segment: EvidenceSegment): Long = 0L

        override suspend fun updateSegment(segment: EvidenceSegment) = Unit
    }

    private object FixedTime : TimeProvider {
        override fun now(): Instant = Instant.parse("2026-08-24T10:00:00Z")

        override fun zone(): ZoneId = ZoneId.of("UTC")
    }
}
