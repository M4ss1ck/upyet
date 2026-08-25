package dev.upyet.evidence.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraUnavailableException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.core.directboot.UserUnlockState
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.time.TimeProvider
import dev.upyet.evidence.data.EvidenceFileStore
import dev.upyet.evidence.domain.EvidenceErrorCode
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceStatus
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.evidence.domain.withFinalized
import dev.upyet.evidence.domain.withStarted
import dev.upyet.evidence.domain.withUnavailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvidenceCoordinator
@Inject
constructor(
    @ApplicationContext private val context: Context,
    // Lazy: nothing on the ringing path may build credential-protected storage before first unlock.
    private val occurrenceRepository: Lazy<OccurrenceRepository>,
    private val evidenceFileStore: EvidenceFileStore,
    private val timeProvider: TimeProvider,
    private val userUnlockState: UserUnlockState,
) {
    /**
     * Process lifetime is justified because recording finalization must still be persisted after the
     * ringing Activity is gone; this scope is never cancelled and only runs short metadata writes.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<EvidenceRecordingState>(EvidenceRecordingState.Idle)
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    private var recording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    val state: StateFlow<EvidenceRecordingState> = _state.asStateFlow()
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    suspend fun start(lifecycleOwner: LifecycleOwner, occurrenceId: OccurrenceId?, evidenceEnabled: Boolean) {
        if (!evidenceEnabled) {
            unavailable(occurrenceId, EvidenceStatus.SKIPPED, EvidenceErrorCode.EVIDENCE_DISABLED)
            return
        }
        if (!userUnlockState.isUserUnlocked()) {
            unavailable(occurrenceId, EvidenceStatus.SKIPPED, EvidenceErrorCode.DIRECT_BOOT_UNAVAILABLE)
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            unavailable(occurrenceId, EvidenceStatus.SKIPPED, EvidenceErrorCode.PERMISSION_DENIED)
            return
        }

        _state.value = EvidenceRecordingState.Preparing
        AlarmLog.event("camera_bind_requested")
        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (error: IllegalArgumentException) {
            initializationFailed(occurrenceId, error)
            return
        } catch (error: IllegalStateException) {
            initializationFailed(occurrenceId, error)
            return
        } catch (error: CameraUnavailableException) {
            initializationFailed(occurrenceId, error)
            return
        } catch (error: ExecutionException) {
            initializationFailed(occurrenceId, error)
            return
        }

        val selector = CameraSelector.DEFAULT_FRONT_CAMERA
        if (!provider.hasCamera(selector)) {
            unavailable(occurrenceId, EvidenceStatus.FAILED, EvidenceErrorCode.CAMERA_UNAVAILABLE)
            return
        }
        val boundPreview = Preview.Builder().build().also {
            it.setSurfaceProvider { request -> _surfaceRequest.value = request }
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)),
            ).build()
        val boundVideoCapture = VideoCapture.withOutput(recorder)
        try {
            provider.bindToLifecycle(lifecycleOwner, selector, boundPreview, boundVideoCapture)
        } catch (error: IllegalArgumentException) {
            initializationFailed(occurrenceId, error)
            return
        } catch (error: IllegalStateException) {
            initializationFailed(occurrenceId, error)
            return
        } catch (error: CameraUnavailableException) {
            initializationFailed(occurrenceId, error)
            return
        }
        preview = boundPreview
        videoCapture = boundVideoCapture
        cameraProvider = provider

        val file = evidenceFileStore.newEvidenceFile()
        val requestedAt = timeProvider.now()
        val segment = occurrenceId?.let {
            EvidenceSegment(0L, it, requestedAt, null, null, null, file.name, null, null, EvidenceStatus.REQUESTED, null)
        }
        val segmentWithId = if (segment != null) {
            segment.copy(id = occurrenceRepository.get().insertSegment(segment))
        } else {
            null
        }
        try {
            val prepared = boundVideoCapture.output.prepareRecording(
                context,
                FileOutputOptions.Builder(file).build(),
            )
            // Every event must see the state the previous one left behind: finalizing a segment that is
            // still REQUESTED because Start never updated it throws out of the CameraX callback.
            var segment = segmentWithId
            recording = prepared.start(ContextCompat.getMainExecutor(context)) { event ->
                segment = handleEvent(event, segment, file)
            }
        } catch (_: IllegalArgumentException) {
            recordingStartFailed(segmentWithId, file)
        } catch (_: IllegalStateException) {
            recordingStartFailed(segmentWithId, file)
        }
    }

    fun stop() {
        recording?.stop()
        recording = null
    }

    fun release() {
        recording?.stop()
        recording = null
        val boundPreview = preview
        val boundVideoCapture = videoCapture
        if (boundPreview != null && boundVideoCapture != null) {
            cameraProvider?.unbind(boundPreview, boundVideoCapture)
        }
        cameraProvider = null
        preview = null
        videoCapture = null
        _surfaceRequest.value = null
    }

    /** Returns the segment as this event leaves it, so the next event works from the current state. */
    private fun handleEvent(event: VideoRecordEvent, segment: EvidenceSegment?, file: File): EvidenceSegment? {
        when (event) {
            is VideoRecordEvent.Start -> {
                val startedAt = timeProvider.now()
                val started = segment?.withStarted(startedAt)
                if (started != null) persist(started)
                _state.value = EvidenceRecordingState.Recording(startedAt, 0L)
                AlarmLog.event("recording_started")
                return started
            }

            is VideoRecordEvent.Status -> {
                val startedAt = (state.value as? EvidenceRecordingState.Recording)?.startedAt ?: return segment
                _state.value = EvidenceRecordingState.Recording(
                    startedAt,
                    event.recordingStats.recordedDurationNanos / NANOS_PER_MILLISECOND,
                )
            }

            is VideoRecordEvent.Finalize -> {
                finalize(event, segment, file)
                return null
            }
        }
        return segment
    }

    private fun finalize(event: VideoRecordEvent.Finalize, segment: EvidenceSegment?, file: File) {
        val durationMs = event.recordingStats.recordedDurationNanos / NANOS_PER_MILLISECOND
        val hasFile = file.isFile && file.length() > 0L
        val (status, errorCode) = statusForFinalize(event.error, hasFile, durationMs)
        if (!hasFile) file.delete()
        segment?.let { pending ->
            val now = timeProvider.now()
            // CameraX can finalize a recording that never reported Start - a camera error, or a stop
            // arriving first - and such a segment is still REQUESTED, which only withUnavailable accepts.
            val updated = if (pending.status == EvidenceStatus.RECORDING) {
                pending.withFinalized(
                    at = now,
                    status = status,
                    durationMs = durationMs,
                    sizeBytes = event.recordingStats.numBytesRecorded,
                    errorCode = errorCode,
                )
            } else {
                pending.withUnavailable(now, EvidenceStatus.FAILED, errorCode ?: EvidenceErrorCode.FINALIZATION_FAILED)
            }
            persist(updated)
        }
        _state.value = EvidenceRecordingState.Finished(status)
        AlarmLog.event("recording_finalized", "status" to status, "error" to errorCode)
        if (errorCode != null) AlarmLog.event("alarm_error", "error" to errorCode)
        recording = null
    }

    private fun persist(segment: EvidenceSegment) {
        scope.launch { occurrenceRepository.get().updateSegment(segment) }
    }

    /**
     * Publishes why evidence is not being captured and, when there is an occurrence to attach it to,
     * persists that reason as a segment so the history screen can explain the gap.
     */
    private fun unavailable(occurrenceId: OccurrenceId?, status: EvidenceStatus, errorCode: EvidenceErrorCode) {
        _state.value = EvidenceRecordingState.Unavailable(status, errorCode)
        if (occurrenceId == null || !userUnlockState.isUserUnlocked()) return
        val now = timeProvider.now()
        scope.launch {
            occurrenceRepository.get().insertSegment(
                EvidenceSegment(
                    id = 0L,
                    occurrenceId = occurrenceId,
                    requestedAt = now,
                    startedAt = null,
                    endedAt = now,
                    finalizedAt = now,
                    fileName = null,
                    durationMs = null,
                    sizeBytes = null,
                    status = status,
                    errorCode = errorCode,
                ),
            )
        }
    }

    private fun initializationFailed(occurrenceId: OccurrenceId?, error: Throwable) {
        val inUse = error.message?.contains("in use", ignoreCase = true) == true
        val code = if (inUse) EvidenceErrorCode.CAMERA_IN_USE else EvidenceErrorCode.INITIALIZATION_FAILED
        unavailable(occurrenceId, EvidenceStatus.FAILED, code)
        AlarmLog.event("alarm_error", "error" to code)
    }

    private fun recordingStartFailed(segment: EvidenceSegment?, file: File) {
        file.delete()
        val now = timeProvider.now()
        val updated = segment?.withUnavailable(now, EvidenceStatus.FAILED, EvidenceErrorCode.RECORDING_START_FAILED)
        if (updated != null) persist(updated)
        // The segment row already carries the reason, so only the UI state needs updating here.
        _state.value = EvidenceRecordingState.Unavailable(EvidenceStatus.FAILED, EvidenceErrorCode.RECORDING_START_FAILED)
        AlarmLog.event("alarm_error", "error" to EvidenceErrorCode.RECORDING_START_FAILED)
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
