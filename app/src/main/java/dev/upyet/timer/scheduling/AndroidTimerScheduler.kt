package dev.upyet.timer.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.alarm.scheduling.ExactAlarmAccess
import dev.upyet.core.logging.AlarmLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The closed carve-out of AGENTS.md §6a and ADR 0009: `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)`,
 * one fixed request code, timers only. Never `setAlarmClock()`, which would publish a countdown as the
 * device's next alarm, and never an inexact fallback when exact access is missing.
 */
@Singleton
class AndroidTimerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val exactAlarmAccess: ExactAlarmAccess,
) : TimerScheduler {
    override fun arm(alarm: TimerAlarm): TimerArmResult {
        if (!exactAlarmAccess.canScheduleExact()) {
            AlarmLog.event("timer_arm_denied", "timerId" to alarm.timerId.value, "reason" to "no_exact_access")
            return TimerArmResult.ExactAlarmsUnavailable
        }
        return try {
            val operation = PendingIntent.getBroadcast(
                context,
                TimerPendingIntents.REQUEST_CODE,
                TimerPendingIntents.expiryIntent(context, alarm),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.triggerAtElapsed.toMillis(), operation)
            AlarmLog.event(
                "timer_armed",
                "timerId" to alarm.timerId.value,
                "triggerAtElapsedMs" to alarm.triggerAtElapsed.toMillis(),
                "endsAt" to alarm.endsAt,
            )
            TimerArmResult.Armed
        } catch (exception: SecurityException) {
            AlarmLog.event("timer_arm_failed", "timerId" to alarm.timerId.value, "error" to exception.javaClass.simpleName)
            TimerArmResult.Failed(exception)
        }
    }

    override fun cancel() {
        PendingIntent.getBroadcast(
            context,
            TimerPendingIntents.REQUEST_CODE,
            TimerPendingIntents.expiryIntent(context, null),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let {
            alarmManager.cancel(it)
            it.cancel()
            AlarmLog.event("timer_alarm_cancelled")
        }
    }
}
