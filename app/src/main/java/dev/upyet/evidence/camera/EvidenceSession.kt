package dev.upyet.evidence.camera

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.core.time.TimeProvider
import dev.upyet.evidence.domain.EvidenceSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What a ringing screen asks evidence to record: the occurrence to attach it to, and whether evidence is on. */
data class EvidenceRequest(val occurrenceId: OccurrenceId?, val evidenceEnabled: Boolean)

/**
 * The one evidence component both ringing screens drive (AGENTS.md §10, ADR 0010): one instance per ringing
 * screen, living as long as that screen's ViewModel. It binds recording to a host lifecycle it is handed,
 * never to an Activity type, writes through the [EvidenceSink] it is handed, and exposes the recording state
 * the UI renders. Recording starts only while the host is visible (invariant 4) and every failure is a state,
 * so nothing here can delay or block ending the ring.
 */
class EvidenceSession(
    private val recorder: EvidenceRecorder,
    private val sink: EvidenceSink,
    // The ViewModel's scope: start and the visible mark are short, and die with the screen that asked for them.
    private val scope: CoroutineScope,
    private val timeProvider: TimeProvider,
) {
    val state: StateFlow<EvidenceRecordingState> = recorder.state
    val surfaceRequest: StateFlow<SurfaceRequest?> = recorder.surfaceRequest

    // activityVisibleAt is the first time the screen was shown, so a re-show after a screen-off must not move it.
    private val markedVisible = mutableSetOf<OccurrenceId>()

    /**
     * Requests recording on every resume and releases the camera on every stop, so a screen-off or a trip to
     * the background ends one segment and the next resume opens another. [currentRequest] is read at resume
     * time, not at bind time. Disposing the handle unbinds and releases.
     */
    fun bind(host: LifecycleOwner, currentRequest: () -> EvidenceRequest?): DisposableHandle {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onVisible(host, currentRequest())
                Lifecycle.Event.ON_STOP -> onHidden()
                else -> Unit
            }
        }
        host.lifecycle.addObserver(observer)
        return DisposableHandle {
            host.lifecycle.removeObserver(observer)
            onHidden()
        }
    }

    /** The host is visible; a null [request] means it is not ringing (yet), so nothing is recorded. */
    fun onVisible(host: LifecycleOwner, request: EvidenceRequest?) {
        if (request == null) return
        val occurrenceId = request.occurrenceId
        if (occurrenceId != null && markedVisible.add(occurrenceId)) {
            scope.launch { sink.markActivityVisible(occurrenceId, timeProvider.now()) }
        }
        scope.launch { recorder.start(host, occurrenceId, request.evidenceEnabled, sink) }
    }

    fun onHidden() {
        recorder.stop()
        recorder.release()
    }

    /** The user ended the ring: CameraX finalizes the segment asynchronously and the ring command never waits for it. */
    fun stopRecording() {
        recorder.stop()
    }
}
