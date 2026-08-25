package dev.upyet.evidence.domain

import dev.upyet.alarm.domain.OccurrenceId
import java.time.Instant

data class EvidenceSegment(
    val id: Long,
    val occurrenceId: OccurrenceId,
    val requestedAt: Instant,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val finalizedAt: Instant?,
    val fileName: String?,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val status: EvidenceStatus,
    val errorCode: EvidenceErrorCode?,
)
