package dev.upyet.core.directboot

import dagger.Lazy
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceStatus
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceRepository
import javax.inject.Inject

/**
 * `USER_UNLOCKED`: copies the occurrences that rang before first unlock into Room, dispatching on each record's source
 * (ADR 0010). Only an alarm record can name a one-time alarm to retire; a timer record never does, and never touches an
 * alarm row. Room is behind [Lazy] and the caller has already checked [UserUnlockState.isUserUnlocked] (AGENTS.md §11).
 */
class PendingOccurrenceReconciler @Inject constructor(
    private val pending: PendingOccurrences,
    private val repository: Lazy<OccurrenceRepository>,
) {
    /**
     * The one-time alarms that rang to an end before unlock, which the caller retires. Timers never appear here.
     * [Retirement.dismissed] tells the caller whether the user dismissed the ring, which decides whether a voice alarm
     * is deleted or only disabled (GLOSSARY.md, Voice alarm).
     */
    data class Result(val alarmsToRetire: List<Retirement>, val alarms: Int, val timers: Int)

    data class Retirement(val alarmId: AlarmId, val dismissed: Boolean)

    suspend fun reconcile(): Result {
        val records = pending.all()
        val retire = records.mapNotNull { record ->
            when (record) {
                is PendingOccurrence -> alarm(record)

                is PendingTimerOccurrence -> {
                    timer(record)
                    null
                }
            }
        }
        pending.clear()
        return Result(retire, records.count { it is PendingOccurrence }, records.count { it is PendingTimerOccurrence })
    }

    private suspend fun alarm(record: PendingOccurrence): Retirement? {
        val repository = repository.get()
        val id = repository.createOccurrence(record.alarmId, record.scheduledFor, record.triggeredAt, null)
        val outcome = record.outcomeOr(OccurrenceOutcome.ERROR)
        repository.completeOccurrence(id, record.dismissedAt, outcome)
        // These alarms rang before first unlock, so the service could not reach the alarm store to retire them.
        // A snoozed one is still due to ring and keeps its place.
        if (outcome == OccurrenceOutcome.SNOOZED) return null
        return Retirement(record.alarmId, dismissed = outcome == OccurrenceOutcome.DISMISSED)
    }

    /**
     * The showing could not record before unlock, so its evidence is one skipped segment carrying why, which History
     * reads as "Unavailable before first unlock" (AGENTS.md §10, §11).
     */
    private suspend fun timer(record: PendingTimerOccurrence) {
        val repository = repository.get()
        val id = repository.createTimerOccurrence(record.timers, record.triggeredAt, record.triggeredAt)
        repository.insertSegment(
            EvidenceSegment(
                id = 0L,
                occurrenceId = id,
                requestedAt = record.triggeredAt,
                startedAt = null,
                endedAt = record.triggeredAt,
                finalizedAt = record.triggeredAt,
                fileName = null,
                durationMs = null,
                sizeBytes = null,
                status = EvidenceStatus.SKIPPED,
                errorCode = record.evidenceError,
            ),
        )
        repository.completeOccurrence(id, record.dismissedAt, record.outcomeOr(OccurrenceOutcome.ERROR))
    }

    private fun PendingRecord.outcomeOr(fallback: OccurrenceOutcome) =
        runCatching { OccurrenceOutcome.valueOf(outcome) }.getOrDefault(fallback)
}
