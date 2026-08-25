package dev.upyet.evidence.camera

import dev.upyet.evidence.domain.EvidenceErrorCode
import dev.upyet.evidence.domain.EvidenceStatus

// Values mirror androidx.camera.video.VideoRecordEvent.Finalize error constants.
private const val ERROR_NONE = 0
private const val ERROR_FILE_SIZE_LIMIT_REACHED = 1
private const val ERROR_DURATION_LIMIT_REACHED = 2
private const val ERROR_SOURCE_INACTIVE = 3
private const val ERROR_INSUFFICIENT_STORAGE = 4
private const val ERROR_NO_VALID_DATA = 5
private const val ERROR_ENCODING_FAILED = 6
private const val ERROR_RECORDER_ERROR = 7

fun statusForFinalize(errorCode: Int, hasFile: Boolean, durationMs: Long): Pair<EvidenceStatus, EvidenceErrorCode?> {
    val hasDuration = hasFile && durationMs > 0L
    return when (errorCode) {
        ERROR_NONE -> EvidenceStatus.RECORDED to null

        ERROR_FILE_SIZE_LIMIT_REACHED, ERROR_DURATION_LIMIT_REACHED, ERROR_SOURCE_INACTIVE ->
            if (hasDuration) {
                EvidenceStatus.PARTIAL to if (errorCode == ERROR_SOURCE_INACTIVE) EvidenceErrorCode.INTERRUPTED else null
            } else {
                EvidenceStatus.FAILED to EvidenceErrorCode.FINALIZATION_FAILED
            }

        ERROR_INSUFFICIENT_STORAGE -> EvidenceStatus.FAILED to EvidenceErrorCode.STORAGE_ERROR

        ERROR_NO_VALID_DATA, ERROR_ENCODING_FAILED, ERROR_RECORDER_ERROR ->
            EvidenceStatus.FAILED to EvidenceErrorCode.FINALIZATION_FAILED

        else -> EvidenceStatus.FAILED to EvidenceErrorCode.UNKNOWN
    }
}
