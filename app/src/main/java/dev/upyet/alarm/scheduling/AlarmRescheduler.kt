package dev.upyet.alarm.scheduling

import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.NextOccurrenceCalculator
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.alarm.domain.UpcomingAlarmSelector
import dev.upyet.core.directboot.AlarmMirror
import dev.upyet.core.directboot.UserUnlockState
import dev.upyet.core.directboot.toAlarm
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.time.TimeProvider
import dev.upyet.settings.data.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRescheduler @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val timeProvider: TimeProvider,
    private val userUnlockState: UserUnlockState,
    private val mirror: AlarmMirror,
    private val settingsRepository: SettingsRepository,
    private val upcomingAlarmScheduler: UpcomingAlarmScheduler,
) {
    data class RescheduleReport(val scheduled: Int, val skipped: Int, val failures: List<AlarmId>, val exactAlarmsUnavailable: Boolean)

    suspend fun rescheduleAll(): RescheduleReport {
        if (!userUnlockState.isUserUnlocked()) return rescheduleMirrors()
        val now = timeProvider.now()
        var scheduled = 0
        var skipped = 0
        var unavailable = false
        val failures = mutableListOf<AlarmId>()
        alarmRepository.observeAlarms().first().forEach { alarm ->
            if (!alarm.enabled) {
                alarmScheduler.cancel(alarm.id, AlarmOccurrenceKind.MAIN)
                skipped++
            } else {
                when (val result = schedule(alarm, now)) {
                    SchedulingResult.Scheduled -> scheduled++
                    SchedulingResult.ExactAlarmsUnavailable -> unavailable = true
                    is SchedulingResult.Failed -> failures += alarm.id
                }
            }
        }
        try {
            refreshUpcoming()
        } catch (error: Exception) {
            AlarmLog.event("upcoming_alarm_error", "error" to error.javaClass.simpleName)
        }
        return RescheduleReport(scheduled, skipped, failures, unavailable)
    }

    suspend fun scheduleNext(alarm: Alarm): SchedulingResult {
        val result = schedule(alarm, timeProvider.now())
        try {
            refreshUpcoming()
        } catch (error: Exception) {
            AlarmLog.event("upcoming_alarm_error", "error" to error.javaClass.simpleName)
        }
        return result
    }

    suspend fun refreshUpcoming() {
        if (!userUnlockState.isUserUnlocked()) return
        try {
            val now = timeProvider.now()
            val leadMinutes = settingsRepository.settings.first().upcomingAlarmLeadMinutes
            val alarms = alarmRepository.observeAlarms().first()
            val candidates = alarms.filter { it.enabled }.mapNotNull { alarm ->
                val trigger =
                    NextOccurrenceCalculator.next(alarm.time, alarm.recurrence, timeProvider.zone(), now, skipOn = alarm.skipNextOn)
                        ?: return@mapNotNull null
                alarm to trigger
            }
            val upcoming = UpcomingAlarmSelector.select(candidates, leadMinutes)
            upcomingAlarmScheduler.sync(upcoming, now)
        } catch (error: Exception) {
            AlarmLog.event("upcoming_alarm_error", "error" to error.javaClass.simpleName)
        }
    }

    /**
     * Switches off a one-time alarm that has finished ringing.
     *
     * A one-time alarm is only a time of day - there is no date to say it is spent - so left enabled
     * the next reschedule rolls it to the same time tomorrow and it comes back as the next alarm.
     * Repeating alarms are untouched: rolling forward is the whole point of them.
     */
    suspend fun retireIfOneTime(alarmId: AlarmId) {
        val alarm = alarmRepository.getAlarm(alarmId) ?: return
        if (alarm.recurrence == Recurrence.OneTime) alarmRepository.setEnabled(alarmId, false)
    }

    private fun schedule(alarm: Alarm, now: Instant): SchedulingResult {
        val trigger =
            NextOccurrenceCalculator.next(alarm.time, alarm.recurrence, timeProvider.zone(), now, skipOn = alarm.skipNextOn)
                ?: return SchedulingResult.Failed(IllegalStateException("No occurrence"))
        return alarmScheduler.schedule(alarm, trigger, AlarmOccurrenceKind.MAIN)
    }

    private fun rescheduleMirrors(): RescheduleReport {
        val now = timeProvider.now()
        var scheduled = 0
        var skipped = 0
        var unavailable = false
        val failures = mutableListOf<AlarmId>()
        mirror.all().forEach { record ->
            val alarm = record.toAlarm()
            // A recurring alarm must recompute its next local occurrence even before first unlock;
            // a one-off or snooze alarm keeps its stored trigger as long as it is still in the future.
            val trigger =
                if (record.kind == AlarmOccurrenceKind.MAIN && alarm.recurrence != Recurrence.OneTime) {
                    NextOccurrenceCalculator.next(alarm.time, alarm.recurrence, timeProvider.zone(), now, skipOn = alarm.skipNextOn)
                } else {
                    record.triggerAt.takeIf { it > now }
                }
            if (trigger == null) {
                skipped++
                return@forEach
            }
            val result = alarmScheduler.schedule(
                alarm,
                trigger,
                record.kind,
                snoozesRemaining = record.snoozesRemaining,
                chainStartedAtMillis = record.chainStartedAtMillis,
            )
            when (result) {
                SchedulingResult.Scheduled -> scheduled++
                SchedulingResult.ExactAlarmsUnavailable -> unavailable = true
                is SchedulingResult.Failed -> failures += record.alarmId
            }
        }
        return RescheduleReport(scheduled, skipped, failures, unavailable)
    }
}
