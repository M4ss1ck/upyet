package dev.upyet.evidence.camera

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.domain.EvidenceSink
import kotlinx.coroutines.flow.StateFlow

/** The CameraX recording engine, as [EvidenceSession] drives it. [EvidenceCoordinator] is the only implementation. */
interface EvidenceRecorder {
    val state: StateFlow<EvidenceRecordingState>
    val surfaceRequest: StateFlow<SurfaceRequest?>

    suspend fun start(host: LifecycleOwner, occurrenceId: OccurrenceId?, evidenceEnabled: Boolean, sink: EvidenceSink)

    /** Ends the current recording; CameraX finalizes it asynchronously. */
    fun stop()

    /** Stops any recording and unbinds the camera use cases from the host. */
    fun release()
}
