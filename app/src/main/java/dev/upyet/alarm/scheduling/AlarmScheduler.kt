package dev.upyet.alarm.scheduling

import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.alarm.domain.SnoozeBudget
import java.time.Instant

interface AlarmScheduler {
    fun schedule(
        alarm: Alarm,
        triggerAt: Instant,
        kind: AlarmOccurrenceKind,
        parentOccurrenceId: OccurrenceId? = null,
        snoozesRemaining: Int = SnoozeBudget.UNSET,
    ): SchedulingResult
    fun cancel(alarmId: AlarmId, kind: AlarmOccurrenceKind)
    fun nextScheduledTrigger(): Instant?
}

sealed interface SchedulingResult {
    data object Scheduled : SchedulingResult
    data object ExactAlarmsUnavailable : SchedulingResult
    data class Failed(val cause: Throwable) : SchedulingResult
}
