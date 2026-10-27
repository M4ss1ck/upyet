package dev.upyet.evidence.domain

import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import java.time.Instant

/**
 * One recorded ringing of an alarm or a timer. [alarmId] is null only for a timer, which has no alarm; [timers] is
 * the snapshot of recording-on timers a timer occurrence covered and is always empty for an alarm.
 */
data class AlarmOccurrence(
    val id: OccurrenceId,
    val alarmId: AlarmId?,
    val scheduledFor: Instant,
    val triggeredAt: Instant?,
    val activityVisibleAt: Instant?,
    val dismissedAt: Instant?,
    val outcome: OccurrenceOutcome,
    val parentOccurrenceId: OccurrenceId?,
    val source: OccurrenceSource = OccurrenceSource.ALARM,
    val timers: List<TimerAttribution> = emptyList(),
) {
    init {
        require(source == OccurrenceSource.TIMER || alarmId != null) { "An alarm occurrence needs an alarm id" }
        require(source == OccurrenceSource.ALARM || (alarmId == null && parentOccurrenceId == null)) {
            "A timer occurrence has no alarm and never a snooze parent"
        }
    }
}
