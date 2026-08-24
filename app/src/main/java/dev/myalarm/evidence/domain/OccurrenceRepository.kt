package dev.myalarm.evidence.domain

import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.OccurrenceId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface OccurrenceRepository {
    fun observeOccurrences(): Flow<List<AlarmOccurrence>>

    fun observeOccurrence(id: OccurrenceId): Flow<AlarmOccurrence?>

    fun observeSegments(occurrenceId: OccurrenceId): Flow<List<EvidenceSegment>>

    suspend fun createOccurrence(
        alarmId: AlarmId,
        scheduledFor: Instant,
        triggeredAt: Instant?,
        parentOccurrenceId: OccurrenceId?,
    ): OccurrenceId

    suspend fun markActivityVisible(
        id: OccurrenceId,
        at: Instant,
    )

    suspend fun completeOccurrence(
        id: OccurrenceId,
        dismissedAt: Instant?,
        outcome: OccurrenceOutcome,
    )

    suspend fun insertSegment(segment: EvidenceSegment): Long

    suspend fun updateSegment(segment: EvidenceSegment)

    suspend fun deleteOccurrence(id: OccurrenceId)
}
