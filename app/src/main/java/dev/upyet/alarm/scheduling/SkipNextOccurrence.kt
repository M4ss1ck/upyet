package dev.upyet.alarm.scheduling

import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.NextOccurrenceCalculator
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.core.time.TimeProvider
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkipNextOccurrence @Inject constructor(
    private val repository: AlarmRepository,
    private val timeProvider: TimeProvider,
    private val rescheduler: AlarmRescheduler,
) {
    suspend fun skip(alarm: Alarm): SchedulingResult? {
        if (!alarm.enabled || alarm.recurrence == Recurrence.OneTime) return null
        val today = timeProvider.now().atZone(timeProvider.zone()).toLocalDate()
        if (alarm.skipNextOn != null && !alarm.skipNextOn.isBefore(today)) return SchedulingResult.Scheduled
        val next = NextOccurrenceCalculator.next(alarm.time, alarm.recurrence, timeProvider.zone(), timeProvider.now())
            ?: return null
        val skipOn: LocalDate = next.atZone(timeProvider.zone()).toLocalDate()
        repository.setSkipNextOn(alarm.id, skipOn)
        val updated = alarm.copy(skipNextOn = skipOn)
        return rescheduler.scheduleNext(updated)
    }

    suspend fun unskip(alarm: Alarm): SchedulingResult? {
        if (!alarm.enabled || alarm.recurrence == Recurrence.OneTime) return null
        val today = timeProvider.now().atZone(timeProvider.zone()).toLocalDate()
        if (alarm.skipNextOn == null || alarm.skipNextOn.isBefore(today)) return SchedulingResult.Scheduled
        repository.setSkipNextOn(alarm.id, null)
        val updated = alarm.copy(skipNextOn = null)
        return rescheduler.scheduleNext(updated)
    }
}
