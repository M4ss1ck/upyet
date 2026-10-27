package dev.upyet.intents

import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import dev.upyet.alarm.scheduling.AlarmRescheduler
import dev.upyet.alarm.scheduling.AlarmScheduler
import dev.upyet.alarm.scheduling.SchedulingResult
import dev.upyet.alarm.scheduling.SkipNextOccurrence
import javax.inject.Inject

/** The production [AlarmActions]: each call delegates to the same collaborators the Alarms tab uses for the same action. */
class DefaultAlarmActions @Inject constructor(
    private val rescheduler: AlarmRescheduler,
    private val skipNextOccurrence: SkipNextOccurrence,
    private val scheduler: AlarmScheduler,
    private val repository: AlarmRepository,
) : AlarmActions {
    override suspend fun schedule(alarm: Alarm): SchedulingResult = rescheduler.scheduleNext(alarm)

    override suspend fun skipNext(alarm: Alarm): SchedulingResult? = skipNextOccurrence.skip(alarm)

    // Mirrors AlarmListViewModel.turnOff step for step, so an intent and a tap leave the same state behind.
    override suspend fun turnOff(alarm: Alarm) {
        scheduler.cancel(alarm.id, AlarmOccurrenceKind.MAIN)
        scheduler.cancel(alarm.id, AlarmOccurrenceKind.SNOOZE)
        // Turning off is indefinite, so a skip must not survive to resurface when the alarm is turned back on.
        if (alarm.skipNextOn != null) repository.setSkipNextOn(alarm.id, null)
        repository.setEnabled(alarm.id, false)
        rescheduler.refreshUpcoming()
    }
}
