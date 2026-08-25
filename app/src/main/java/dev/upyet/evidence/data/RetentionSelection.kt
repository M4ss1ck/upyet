package dev.upyet.evidence.data

import dev.upyet.core.database.EvidenceSegmentEntity
import dev.upyet.settings.data.RetentionPolicy

fun selectSegmentsForRetention(
    segments: List<EvidenceSegmentEntity>,
    policy: RetentionPolicy,
    cutoffMillis: Long,
): List<EvidenceSegmentEntity> = if (policy == RetentionPolicy.FOREVER) {
    emptyList()
} else {
    segments.filter {
        it.requestedAt < cutoffMillis && it.status != "REQUESTED" && it.status != "RECORDING"
    }
}
