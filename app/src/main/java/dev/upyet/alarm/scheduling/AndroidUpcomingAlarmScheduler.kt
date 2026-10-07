package dev.upyet.alarm.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.alarm.domain.UpcomingAlarm
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.notifications.AlarmNotifications
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidUpcomingAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val notifications: AlarmNotifications,
) : UpcomingAlarmScheduler {
    override fun sync(upcoming: UpcomingAlarm?, now: Instant) {
        // Always cancel any pending work first: at most one upcoming notification and one pending intent exist globally.
        cancelPendingIntent()
        notifications.cancelUpcoming()
        if (upcoming == null) return
        try {
            if (now >= upcoming.postAt) {
                // Inside the lead window already (reboot, settings change) - post immediately.
                notifications.postUpcoming(upcoming.alarmId.value, upcoming.label, upcoming.triggerAt, upcoming.canSkip)
            } else {
                // Silent shade notification an hour ahead does not need second precision; exact alarms are
                // reserved for real occurrences, and plain set() can be deferred past the alarm itself in deep Doze.
                val pending = upcomingPendingIntent(upcoming)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    upcoming.postAt.toEpochMilli(),
                    pending,
                )
            }
        } catch (error: Exception) {
            AlarmLog.event("upcoming_alarm_error", "error" to error.javaClass.simpleName)
        }
    }

    override fun cancelNotification() {
        cancelPendingIntent()
        notifications.cancelUpcoming()
    }

    private fun cancelPendingIntent() {
        val cancelIntent = Intent(context, UpcomingAlarmReceiver::class.java).apply { action = UpcomingAlarmReceiver.ACTION_UPCOMING_ALARM }
        PendingIntent.getBroadcast(
            context,
            UpcomingAlarmReceiver.REQUEST_UPCOMING_ALARM,
            cancelIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        val skipIntent = Intent(context, UpcomingAlarmReceiver::class.java).apply {
            action = UpcomingAlarmReceiver.ACTION_UPCOMING_ALARM_SKIP
        }
        // The skip action's PendingIntent is owned by the notification; cancelling the posted notification
        // above removes it from the shade, but we also clear any exact copy that might still be registered.
        PendingIntent.getBroadcast(
            context,
            UpcomingAlarmReceiver.REQUEST_UPCOMING_ALARM_SKIP,
            skipIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.cancel()
    }

    private fun upcomingPendingIntent(upcoming: UpcomingAlarm): PendingIntent {
        val intent = Intent(context, UpcomingAlarmReceiver::class.java).apply {
            action = UpcomingAlarmReceiver.ACTION_UPCOMING_ALARM
            putExtra(UpcomingAlarmReceiver.EXTRA_ALARM_ID, upcoming.alarmId.value)
            putExtra(UpcomingAlarmReceiver.EXTRA_LABEL, upcoming.label)
            putExtra(UpcomingAlarmReceiver.EXTRA_TRIGGER_AT, upcoming.triggerAt.toEpochMilli())
            putExtra(UpcomingAlarmReceiver.EXTRA_CAN_SKIP, upcoming.canSkip)
        }
        return PendingIntent.getBroadcast(
            context,
            UpcomingAlarmReceiver.REQUEST_UPCOMING_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
