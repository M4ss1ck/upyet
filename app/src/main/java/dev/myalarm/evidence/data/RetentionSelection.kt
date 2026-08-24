package dev.myalarm.evidence.data

import dev.myalarm.core.database.EvidenceSegmentEntity
import dev.myalarm.settings.data.RetentionPolicy

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
