package dev.upyet.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.MainActivity
import dev.upyet.R
import dev.upyet.alarm.playback.AlarmPlaybackService
import dev.upyet.alarm.ringing.RingingActivity
import dev.upyet.alarm.scheduling.UpcomingAlarmReceiver
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    fun ensureChannels() {
        val ringing = NotificationChannel(
            CHANNEL_RINGING,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        // An audible nudge before the alarm is a second alarm the user did not ask for; this stays silent.
        val upcoming = NotificationChannel(
            CHANNEL_UPCOMING,
            context.getString(R.string.upcoming_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(ringing)
        manager?.createNotificationChannel(upcoming)
    }

    fun buildRingingNotification(alarmLabel: String?, isUserUnlocked: Boolean): Notification {
        val activityIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_RINGING,
            Intent(context, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(context.getString(R.string.alarm_ringing_title))
            .setContentText(
                if (isUserUnlocked) {
                    alarmLabel?.takeIf {
                        it.isNotBlank()
                    } ?: context.getString(R.string.alarm_ringing_text)
                } else {
                    context.getString(R.string.alarm_ringing_text)
                },
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(activityIntent, true)
            .setContentIntent(activityIntent)
            // While the device is in use the platform shows this as a heads-up instead of launching the
            // full-screen intent, so the alarm has to be answerable from the notification itself.
            .addAction(
                0,
                context.getString(R.string.alarm_notification_snooze),
                command(AlarmPlaybackService.snoozeIntent(context), REQUEST_SNOOZE),
            )
            .addAction(
                0,
                context.getString(R.string.alarm_notification_dismiss),
                command(AlarmPlaybackService.dismissIntent(context), REQUEST_DISMISS),
            )
            .build()
    }

    fun postUpcoming(alarmId: Long, label: String?, triggerAt: Instant, canSkip: Boolean) {
        ensureChannels()
        val timeText = triggerAt.atZone(ZoneId.systemDefault()).toLocalTime()
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val contentText = label?.takeIf { it.isNotBlank() } ?: context.getString(R.string.upcoming_alarm_text)
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_UPCOMING,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val skipIntent = Intent(context, UpcomingAlarmReceiver::class.java).apply {
            action = UpcomingAlarmReceiver.ACTION_UPCOMING_ALARM_SKIP
            putExtra(UpcomingAlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(UpcomingAlarmReceiver.EXTRA_CAN_SKIP, canSkip)
        }
        val skipPending = PendingIntent.getBroadcast(
            context,
            UpcomingAlarmReceiver.REQUEST_UPCOMING_ALARM_SKIP,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val actionTitle = if (canSkip) {
            context.getString(R.string.upcoming_skip)
        } else {
            context.getString(R.string.upcoming_turn_off)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_UPCOMING)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(timeText)
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .setAutoCancel(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .addAction(0, actionTitle, skipPending)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID_UPCOMING, notification)
    }

    fun cancelUpcoming() {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID_UPCOMING)
    }

    /** The service is already in the foreground, and a notification action may start it from anywhere. */
    private fun command(intent: Intent, requestCode: Int): PendingIntent = PendingIntent.getForegroundService(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Below API 34 the platform grants full-screen intents to alarm-category notifications outright. */
    fun canUseFullScreenIntent(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    fun areNotificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    companion object {
        const val CHANNEL_RINGING = "alarm_ringing"
        const val CHANNEL_UPCOMING = "alarm_upcoming"
        const val NOTIFICATION_ID_RINGING = 1001
        const val NOTIFICATION_ID_UPCOMING = 1004
        private const val REQUEST_SNOOZE = 1002
        private const val REQUEST_DISMISS = 1003
    }
}
