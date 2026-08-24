package dev.myalarm.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.myalarm.R
import dev.myalarm.alarm.ringing.RingingActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
            Intent(context, RingingActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
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
            .setSilent(true)
            .setFullScreenIntent(activityIntent, true)
            .setContentIntent(activityIntent)
            .build()
    }

    fun canUseFullScreenIntent(): Boolean = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    fun areNotificationsEnabled(): Boolean = Build.VERSION.SDK_INT < 33 ||
        context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true

    companion object {
        const val CHANNEL_RINGING = "alarm_ringing"
        const val NOTIFICATION_ID_RINGING = 1001
    }
}
