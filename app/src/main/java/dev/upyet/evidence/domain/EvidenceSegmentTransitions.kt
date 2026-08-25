package dev.upyet.evidence.domain

import java.time.Instant

fun EvidenceSegment.withStarted(at: Instant): EvidenceSegment {
    check(status == EvidenceStatus.REQUESTED) { "Only requested segments can start" }
    return copy(startedAt = at, status = EvidenceStatus.RECORDING)
}

fun EvidenceSegment.withFinalized(
    at: Instant,
    status: EvidenceStatus,
    durationMs: Long?,
    sizeBytes: Long?,
    errorCode: EvidenceErrorCode? = null,
): EvidenceSegment {
    check(this.status == EvidenceStatus.RECORDING) { "Only recording segments can finalize" }
    check(status == EvidenceStatus.RECORDED || status == EvidenceStatus.PARTIAL || status == EvidenceStatus.FAILED) {
        "Finalized segments must be recorded, partial, or failed"
    }
    return copy(
        endedAt = at,
        finalizedAt = at,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        status = status,
        errorCode = errorCode,
    )
}

fun EvidenceSegment.withUnavailable(at: Instant, status: EvidenceStatus, errorCode: EvidenceErrorCode?): EvidenceSegment {
    check(this.status == EvidenceStatus.REQUESTED) { "Only requested segments can be skipped or failed" }
    check(status == EvidenceStatus.FAILED || status == EvidenceStatus.SKIPPED) {
        "Unavailable segments must be failed or skipped"
    }
    return copy(endedAt = at, finalizedAt = at, status = status, errorCode = errorCode)
}
