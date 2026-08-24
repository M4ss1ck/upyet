package dev.myalarm.alarm.scheduling

import dev.myalarm.alarm.domain.Alarm
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.AlarmRepository
import dev.myalarm.alarm.domain.NextOccurrenceCalculator
import dev.myalarm.alarm.domain.OccurrenceId
import dev.myalarm.alarm.domain.Recurrence
import dev.myalarm.core.directboot.AlarmMirror
import dev.myalarm.core.directboot.MirroredAlarm
import dev.myalarm.core.directboot.UserUnlockState
import dev.myalarm.core.time.TimeProvider
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRescheduler @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val timeProvider: TimeProvider,
    private val userUnlockState: UserUnlockState,
    private val mirror: AlarmMirror,
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
        return RescheduleReport(scheduled, skipped, failures, unavailable)
    }

    suspend fun scheduleNext(alarm: Alarm): SchedulingResult = schedule(alarm, timeProvider.now())

    private fun schedule(alarm: Alarm, now: Instant): SchedulingResult {
        val trigger =
            NextOccurrenceCalculator.next(alarm.time, alarm.recurrence, timeProvider.zone(), now)
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
            if (record.triggerAt <= now) {
                skipped++
            } else {
                val alarm = Alarm(
                    id = record.alarmId,
                    time = record.triggerAt.atZone(timeProvider.zone()).toLocalTime(),
                    enabled = true,
                    label = "",
                    recurrence = Recurrence.OneTime,
                    soundUri = record.soundUri,
                    vibrationEnabled = record.vibrationEnabled,
                    snoozeMinutes = record.snoozeMinutes,
                    evidenceEnabled = false,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                )
                val result =
                    alarmScheduler.schedule(alarm, record.triggerAt, record.kind)
                when (result) {
                    SchedulingResult.Scheduled -> scheduled++
                    SchedulingResult.ExactAlarmsUnavailable -> unavailable = true
                    is SchedulingResult.Failed -> failures += record.alarmId
                }
            }
        }
        return RescheduleReport(scheduled, skipped, failures, unavailable)
    }
}
