package dev.upyet.evidence.data

import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.core.database.AlarmOccurrenceEntity
import dev.upyet.core.database.EvidenceSegmentDao
import dev.upyet.core.database.EvidenceSegmentEntity
import dev.upyet.core.database.OccurrenceDao
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceRepository
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

    override fun observeOccurrenceWithSegments(id: OccurrenceId): Flow<OccurrenceRepository.OccurrenceWithEvidence?> =
        occurrenceDao.observeWithSegments(id.value).map { value ->
            value?.let {
                OccurrenceRepository.OccurrenceWithEvidence(it.occurrence.toDomain(), it.segments.map(EvidenceSegmentEntity::toDomain))
            }
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
