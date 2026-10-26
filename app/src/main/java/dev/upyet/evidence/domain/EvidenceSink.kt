package dev.upyet.evidence.domain

import dev.upyet.alarm.domain.OccurrenceId
import java.time.Instant

/**
 * Where the evidence component writes what it learns about one occurrence: when its ringing screen became
 * visible, and its segments. The ringing screen hands it in, so the component never decides which storage
 * an occurrence lives in (AGENTS.md §10).
 */
interface EvidenceSink {
    suspend fun markActivityVisible(id: OccurrenceId, at: Instant)

    suspend fun insertSegment(segment: EvidenceSegment): Long

    suspend fun updateSegment(segment: EvidenceSegment)
}
