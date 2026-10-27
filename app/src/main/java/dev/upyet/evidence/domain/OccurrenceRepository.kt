package dev.upyet.evidence.domain

import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface OccurrenceRepository : EvidenceSink {
    data class OccurrenceWithEvidence(val occurrence: AlarmOccurrence, val segments: List<EvidenceSegment>)

    fun observeOccurrences(): Flow<List<AlarmOccurrence>>

    fun observeOccurrence(id: OccurrenceId): Flow<AlarmOccurrence?>

    fun observeSegments(occurrenceId: OccurrenceId): Flow<List<EvidenceSegment>>

    fun observeOccurrenceWithSegments(id: OccurrenceId): Flow<OccurrenceWithEvidence?>

    suspend fun createOccurrence(
        alarmId: AlarmId,
        scheduledFor: Instant,
        triggeredAt: Instant?,
        parentOccurrenceId: OccurrenceId?,
    ): OccurrenceId

    /** Creates a ringing timer occurrence. The attribution is a snapshot; no alarm and no parent are involved. */
    suspend fun createTimerOccurrence(timers: List<TimerAttribution>, scheduledFor: Instant, triggeredAt: Instant): OccurrenceId

    /** Replaces the snapshot of recording-on timers while the showing that owns this occurrence is still open. */
    suspend fun updateTimerAttribution(id: OccurrenceId, timers: List<TimerAttribution>)

    /**
     * Interrupts every timer occurrence still ringing. Only the process-scoped showing ever holds one open, so any other is
     * a showing whose process died before it could close it. Its user-ended time stays null: nobody ended it.
     */
    suspend fun interruptOrphanedTimerOccurrences()

    suspend fun completeOccurrence(id: OccurrenceId, dismissedAt: Instant?, outcome: OccurrenceOutcome)

    suspend fun deleteOccurrence(id: OccurrenceId)
}
