package dev.upyet.evidence.domain

import java.time.Instant

fun AlarmOccurrence.withOutcome(outcome: OccurrenceOutcome, at: Instant? = null): AlarmOccurrence {
    check(this.outcome == OccurrenceOutcome.RINGING) { "Only ringing occurrences can finish" }
    when (source) {
        OccurrenceSource.ALARM -> check(
            outcome == OccurrenceOutcome.DISMISSED || outcome == OccurrenceOutcome.SNOOZED || outcome == OccurrenceOutcome.TIMED_OUT,
        ) { "An alarm occurrence can only be dismissed, snoozed or timed out" }

        // A timer is ended by Stop or +1 min on the ringing screen, never dismissed, snoozed or timed out.
        OccurrenceSource.TIMER -> check(outcome == OccurrenceOutcome.STOPPED) { "A timer occurrence can only be stopped" }
    }
    return copy(outcome = outcome, dismissedAt = at ?: dismissedAt)
}

fun AlarmOccurrence.snoozedChild(id: dev.upyet.alarm.domain.OccurrenceId, scheduledFor: Instant): AlarmOccurrence {
    check(source == OccurrenceSource.ALARM) { "a timer occurrence never snoozes" }
    return copy(
        id = id,
        scheduledFor = scheduledFor,
        triggeredAt = null,
        activityVisibleAt = null,
        dismissedAt = null,
        outcome = OccurrenceOutcome.RINGING,
        parentOccurrenceId = this.id,
    )
}
