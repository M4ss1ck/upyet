package dev.myalarm.evidence.domain

import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.OccurrenceId
import java.time.Instant

data class AlarmOccurrence(
    val id: OccurrenceId,
    val alarmId: AlarmId,
    val scheduledFor: Instant,
    val triggeredAt: Instant?,
    val activityVisibleAt: Instant?,
    val dismissedAt: Instant?,
    val outcome: OccurrenceOutcome,
    val parentOccurrenceId: OccurrenceId?,
)
