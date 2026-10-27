package dev.upyet.timer.ringing

import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.evidence.domain.TimerAttribution
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Records the timer occurrence writes the showing makes and hands out ids 1, 2, ... in creation order. The
 * evidence writes are recorded too, so a test can see which occurrence a segment would have attached to.
 * Failures are set per test; the default fake never fails.
 */
class FakeOccurrenceRepository : OccurrenceRepository {
    data class Created(val id: OccurrenceId, val timers: List<TimerAttribution>, val scheduledFor: Instant, val triggeredAt: Instant)

    data class Completed(val id: OccurrenceId, val dismissedAt: Instant?, val outcome: OccurrenceOutcome)

    val created = mutableListOf<Created>()
    val updated = mutableListOf<Pair<OccurrenceId, List<TimerAttribution>>>()
    val completed = mutableListOf<Completed>()
    val visible = mutableListOf<OccurrenceId>()

    /** Every write in order, so a test can assert the sweep runs before the create. */
    val calls = mutableListOf<String>()
    var sweeps = 0
    var sweepFailure: Throwable? = null

    var createFailure: Throwable? = null
    var updateFailure: Throwable? = null

    private var nextId = 1L

    override suspend fun createTimerOccurrence(timers: List<TimerAttribution>, scheduledFor: Instant, triggeredAt: Instant): OccurrenceId {
        createFailure?.let { throw it }
        val id = OccurrenceId(nextId++)
        created += Created(id, timers, scheduledFor, triggeredAt)
        calls += "create"
        return id
    }

    override suspend fun interruptOrphanedTimerOccurrences() {
        sweepFailure?.let { throw it }
        sweeps++
        calls += "sweep"
    }

    override suspend fun updateTimerAttribution(id: OccurrenceId, timers: List<TimerAttribution>) {
        updateFailure?.let { throw it }
        updated += id to timers
    }

    override suspend fun completeOccurrence(id: OccurrenceId, dismissedAt: Instant?, outcome: OccurrenceOutcome) {
        completed += Completed(id, dismissedAt, outcome)
    }

    override suspend fun markActivityVisible(id: OccurrenceId, at: Instant) {
        visible += id
    }

    override suspend fun insertSegment(segment: EvidenceSegment): Long = error("segments are not part of this fake")

    override suspend fun updateSegment(segment: EvidenceSegment) = error("segments are not part of this fake")

    override fun observeOccurrences(): Flow<List<AlarmOccurrence>> = error("unused")

    override fun observeOccurrence(id: OccurrenceId): Flow<AlarmOccurrence?> = error("unused")

    override fun observeSegments(occurrenceId: OccurrenceId): Flow<List<EvidenceSegment>> = error("unused")

    override fun observeOccurrenceWithSegments(id: OccurrenceId): Flow<OccurrenceRepository.OccurrenceWithEvidence?> = error("unused")

    override suspend fun createOccurrence(
        alarmId: AlarmId,
        scheduledFor: Instant,
        triggeredAt: Instant?,
        parentOccurrenceId: OccurrenceId?,
    ): OccurrenceId = error("alarm occurrences are not part of this fake")

    override suspend fun deleteOccurrence(id: OccurrenceId) = error("unused")
}
