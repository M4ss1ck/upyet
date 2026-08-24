package dev.myalarm.alarm.scheduling

import dev.myalarm.alarm.domain.Alarm
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.OccurrenceId
import java.time.Instant

interface AlarmScheduler {
    fun schedule(alarm: Alarm, triggerAt: Instant, kind: AlarmOccurrenceKind, parentOccurrenceId: OccurrenceId? = null): SchedulingResult
    fun cancel(alarmId: AlarmId, kind: AlarmOccurrenceKind)
    fun nextScheduledTrigger(): Instant?
}

sealed interface SchedulingResult {
    data object Scheduled : SchedulingResult
    data object ExactAlarmsUnavailable : SchedulingResult
    data class Failed(val cause: Throwable) : SchedulingResult
}
