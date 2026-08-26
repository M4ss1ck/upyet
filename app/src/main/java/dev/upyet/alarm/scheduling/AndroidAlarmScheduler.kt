package dev.upyet.alarm.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.MainActivity
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.alarm.domain.SnoozeBudget
import dev.upyet.core.directboot.AlarmMirror
import dev.upyet.core.directboot.MirroredAlarm
import dev.upyet.core.directboot.MirroredRecurrence
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val exactAlarmAccess: ExactAlarmAccess,
    private val mirror: AlarmMirror,
) : AlarmScheduler {
    override fun schedule(
        alarm: Alarm,
        triggerAt: Instant,
        kind: AlarmOccurrenceKind,
        parentOccurrenceId: OccurrenceId?,
        snoozesRemaining: Int,
        chainStartedAtMillis: Long,
    ): SchedulingResult {
        if (!exactAlarmAccess.canScheduleExact()) return SchedulingResult.ExactAlarmsUnavailable
        return try {
            val operation = PendingIntent.getBroadcast(
                context,
                AlarmPendingIntents.requestCode(alarm.id, kind),
                AlarmPendingIntents.alarmIntent(
                    context,
                    alarm.id,
                    kind,
                    triggerAt,
                    parentOccurrenceId,
                    snoozesRemaining,
                    chainStartedAtMillis,
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val showIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt.toEpochMilli(), showIntent), operation)
            val mask = (alarm.recurrence as? dev.upyet.alarm.domain.Recurrence.Weekly)
                ?.days?.sumOf { 1 shl (it.value - 1) } ?: 0
            val recurrenceType = when (alarm.recurrence) {
                dev.upyet.alarm.domain.Recurrence.OneTime -> "ONE_TIME"
                dev.upyet.alarm.domain.Recurrence.Daily -> "DAILY"
                is dev.upyet.alarm.domain.Recurrence.Weekly -> "WEEKLY"
            }
            mirror.put(
                MirroredAlarm(
                    alarm.id,
                    kind,
                    triggerAt,
                    alarm.snoozeMinutes,
                    alarm.vibrationEnabled,
                    alarm.soundUri,
                    alarm.time.toSecondOfDay() / 60,
                    recurrenceType,
                    mask,
                    snoozesRemaining,
                    chainStartedAtMillis,
                    alarm.skipNextOn?.toEpochDay(),
                ),
            )
            SchedulingResult.Scheduled
        } catch (exception: SecurityException) {
            SchedulingResult.Failed(exception)
        }
    }

    override fun cancel(alarmId: AlarmId, kind: AlarmOccurrenceKind) {
        val intent = AlarmPendingIntents.alarmIntent(context, alarmId, kind, Instant.EPOCH, null, SnoozeBudget.UNSET)
        PendingIntent.getBroadcast(
            context,
            AlarmPendingIntents.requestCode(alarmId, kind),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        mirror.remove(alarmId, kind)
    }

    override fun nextScheduledTrigger(): Instant? = mirror.nextTrigger()

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}
