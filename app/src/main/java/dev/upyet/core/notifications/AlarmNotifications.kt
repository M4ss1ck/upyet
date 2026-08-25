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
import dev.upyet.R
import dev.upyet.alarm.playback.AlarmPlaybackService
import dev.upyet.alarm.ringing.RingingActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    fun ensureChannels() {
        val channel = NotificationChannel(
            CHANNEL_RINGING,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
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
        const val NOTIFICATION_ID_RINGING = 1001
        private const val REQUEST_SNOOZE = 1002
        private const val REQUEST_DISMISS = 1003
    }
}
