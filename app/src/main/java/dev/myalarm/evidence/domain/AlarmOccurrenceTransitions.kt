package dev.myalarm.evidence.domain

import java.time.Instant

fun AlarmOccurrence.withOutcome(outcome: OccurrenceOutcome, at: Instant? = null): AlarmOccurrence {
    check(this.outcome == OccurrenceOutcome.RINGING) { "Only ringing occurrences can finish" }
    check(outcome == OccurrenceOutcome.DISMISSED || outcome == OccurrenceOutcome.SNOOZED || outcome == OccurrenceOutcome.TIMED_OUT) {
        "Unsupported outcome transition"
    }
    return copy(outcome = outcome, dismissedAt = at ?: dismissedAt)
}

fun AlarmOccurrence.snoozedChild(id: dev.myalarm.alarm.domain.OccurrenceId, scheduledFor: Instant): AlarmOccurrence = copy(
    id = id,
    scheduledFor = scheduledFor,
    triggeredAt = null,
    activityVisibleAt = null,
    dismissedAt = null,
    outcome = OccurrenceOutcome.RINGING,
    parentOccurrenceId = this.id,
)
