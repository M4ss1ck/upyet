package dev.upyet.history.ui

import androidx.annotation.StringRes
import dev.upyet.R
import dev.upyet.evidence.domain.EvidenceErrorCode
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceStatus

@StringRes
fun evidenceSummaryResource(status: EvidenceStatus, error: EvidenceErrorCode?): Int = when {
    status == EvidenceStatus.RECORDED -> R.string.evidence_summary_recorded

    status == EvidenceStatus.PARTIAL -> R.string.evidence_summary_partial

    error == EvidenceErrorCode.PERMISSION_DENIED -> R.string.evidence_summary_permission_denied

    error == EvidenceErrorCode.CAMERA_UNAVAILABLE || error == EvidenceErrorCode.CAMERA_IN_USE ->
        R.string.evidence_summary_camera_unavailable

    error == EvidenceErrorCode.INITIALIZATION_FAILED -> R.string.evidence_summary_initialization_failed

    error == EvidenceErrorCode.STORAGE_ERROR -> R.string.evidence_summary_storage_error

    error == EvidenceErrorCode.DIRECT_BOOT_UNAVAILABLE -> R.string.evidence_summary_direct_boot

    error != null -> R.string.evidence_summary_unknown

    else -> R.string.evidence_summary_not_recorded
}

@StringRes
fun evidenceSummaryResource(segments: List<EvidenceSegment>): Int {
    if (segments.any { it.status == EvidenceStatus.PARTIAL }) return R.string.evidence_summary_partial
    val firstFailure = segments.firstOrNull { it.status == EvidenceStatus.FAILED || it.status == EvidenceStatus.SKIPPED }
    return if (firstFailure ==
        null
    ) {
        R.string.evidence_summary_recorded
    } else {
        evidenceSummaryResource(firstFailure.status, firstFailure.errorCode)
    }
}
