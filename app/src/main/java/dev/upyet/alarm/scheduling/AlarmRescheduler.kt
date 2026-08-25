package dev.upyet.alarm.scheduling

import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.NextOccurrenceCalculator
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.core.directboot.AlarmMirror
import dev.upyet.core.directboot.UserUnlockState
import dev.upyet.core.directboot.toAlarm
import dev.upyet.core.time.TimeProvider
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
            val alarm = record.toAlarm()
            // A recurring alarm must recompute its next local occurrence even before first unlock;
            // a one-off or snooze alarm keeps its stored trigger as long as it is still in the future.
            val trigger =
                if (record.kind == AlarmOccurrenceKind.MAIN && alarm.recurrence != Recurrence.OneTime) {
                    NextOccurrenceCalculator.next(alarm.time, alarm.recurrence, timeProvider.zone(), now)
                } else {
                    record.triggerAt.takeIf { it > now }
                }
            if (trigger == null) {
                skipped++
                return@forEach
            }
            val result = alarmScheduler.schedule(alarm, trigger, record.kind)
            when (result) {
                SchedulingResult.Scheduled -> scheduled++
                SchedulingResult.ExactAlarmsUnavailable -> unavailable = true
                is SchedulingResult.Failed -> failures += record.alarmId
            }
        }
        return RescheduleReport(scheduled, skipped, failures, unavailable)
    }
}
