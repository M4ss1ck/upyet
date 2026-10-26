package dev.upyet.evidence.data

import dagger.Lazy
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceSink
import dev.upyet.evidence.domain.OccurrenceRepository
import java.time.Instant

/**
 * Resolves the repository on the first write, never on construction. A ringing screen can be shown before
 * first unlock, and nothing on that path may build credential-protected Room until the user is unlocked
 * (AGENTS.md §11, §14); the evidence component only writes once it has checked that.
 */
class DeferredEvidenceSink(private val repository: Lazy<OccurrenceRepository>) : EvidenceSink {
    override suspend fun markActivityVisible(id: OccurrenceId, at: Instant) = repository.get().markActivityVisible(id, at)

    override suspend fun insertSegment(segment: EvidenceSegment): Long = repository.get().insertSegment(segment)

    override suspend fun updateSegment(segment: EvidenceSegment) = repository.get().updateSegment(segment)
}
