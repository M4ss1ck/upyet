package dev.myalarm.alarm.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.myalarm.MainActivity
import dev.myalarm.alarm.domain.Alarm
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.OccurrenceId
import dev.myalarm.core.directboot.AlarmMirror
import dev.myalarm.core.directboot.MirroredAlarm
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
    ): SchedulingResult {
        if (!exactAlarmAccess.canScheduleExact()) return SchedulingResult.ExactAlarmsUnavailable
        return try {
            val operation = PendingIntent.getBroadcast(
                context,
                AlarmPendingIntents.requestCode(alarm.id, kind),
                AlarmPendingIntents.alarmIntent(context, alarm.id, kind, triggerAt, parentOccurrenceId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val showIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt.toEpochMilli(), showIntent), operation)
            mirror.put(MirroredAlarm(alarm.id, kind, triggerAt, alarm.snoozeMinutes, alarm.vibrationEnabled, alarm.soundUri))
            SchedulingResult.Scheduled
        } catch (exception: SecurityException) {
            SchedulingResult.Failed(exception)
        }
    }

    override fun cancel(alarmId: AlarmId, kind: AlarmOccurrenceKind) {
        val intent = AlarmPendingIntents.alarmIntent(context, alarmId, kind, Instant.EPOCH, null)
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
}
