package dev.myalarm.evidence.data

import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.OccurrenceId
import dev.myalarm.core.database.AlarmOccurrenceEntity
import dev.myalarm.core.database.EvidenceSegmentDao
import dev.myalarm.core.database.EvidenceSegmentEntity
import dev.myalarm.core.database.OccurrenceDao
import dev.myalarm.evidence.domain.AlarmOccurrence
import dev.myalarm.evidence.domain.EvidenceSegment
import dev.myalarm.evidence.domain.OccurrenceOutcome
import dev.myalarm.evidence.domain.OccurrenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomOccurrenceRepository
@Inject
constructor(
    private val occurrenceDao: OccurrenceDao,
    private val evidenceSegmentDao: EvidenceSegmentDao,
) : OccurrenceRepository {
    override fun observeOccurrences(): Flow<List<AlarmOccurrence>> =
        occurrenceDao.observeAll().map { it.map(AlarmOccurrenceEntity::toDomain) }

    override fun observeOccurrence(id: OccurrenceId): Flow<AlarmOccurrence?> = occurrenceDao.observeById(id.value).map { it?.toDomain() }

    override fun observeSegments(occurrenceId: OccurrenceId): Flow<List<EvidenceSegment>> {
        val segments = evidenceSegmentDao.observeForOccurrence(occurrenceId.value)
        return segments.map { entities -> entities.map(EvidenceSegmentEntity::toDomain) }
    }

    override suspend fun createOccurrence(
        alarmId: AlarmId,
        scheduledFor: Instant,
        triggeredAt: Instant?,
        parentOccurrenceId: OccurrenceId?,
    ): OccurrenceId {
        val entity =
            AlarmOccurrenceEntity(
                alarmId = alarmId.value,
                scheduledFor = scheduledFor.toEpochMilli(),
                triggeredAt = triggeredAt?.toEpochMilli(),
                activityVisibleAt = null,
                dismissedAt = null,
                outcome = OccurrenceOutcome.RINGING.name,
                parentOccurrenceId = parentOccurrenceId?.value,
            )
        return OccurrenceId(occurrenceDao.insert(entity))
    }

    override suspend fun markActivityVisible(id: OccurrenceId, at: Instant) {
        occurrenceDao.markActivityVisible(id.value, at.toEpochMilli())
    }

    override suspend fun completeOccurrence(id: OccurrenceId, dismissedAt: Instant?, outcome: OccurrenceOutcome) {
        occurrenceDao.complete(
            id = id.value,
            dismissedAt = dismissedAt?.toEpochMilli(),
            outcome = outcome.name,
        )
    }

    override suspend fun insertSegment(segment: EvidenceSegment): Long = evidenceSegmentDao.insert(segment.toEntity())

    override suspend fun updateSegment(segment: EvidenceSegment) {
        evidenceSegmentDao.upsert(segment.toEntity())
    }

    override suspend fun deleteOccurrence(id: OccurrenceId) {
        occurrenceDao.deleteById(id.value)
    }
}
