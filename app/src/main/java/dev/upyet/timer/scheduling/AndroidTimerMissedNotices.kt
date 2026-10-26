package dev.upyet.timer.scheduling

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.MainActivity
import dev.upyet.R
import dev.upyet.core.notifications.AlarmNotifications
import dev.upyet.timer.domain.Timer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The missed-timer notice on ADR 0012's `timer_missed` channel, id 1103: low importance, silent, not ongoing,
 * and not auto-cancelled, so it stays until Reset or delete leaves no timer Missed ([MissedNotice]). Built
 * here beside discovery rather than in [AlarmNotifications], which only owns the channel. Touches no Room:
 * discovery posts it before first unlock.
 */
@Singleton
class AndroidTimerMissedNotices @Inject constructor(@ApplicationContext private val context: Context) : TimerMissedNotices {
    private val manager: NotificationManager? get() = context.getSystemService(NotificationManager::class.java)

    override fun isShowing(): Boolean =
        manager?.activeNotifications?.any { it.id == AlarmNotifications.NOTIFICATION_ID_TIMER_MISSED } == true

    override fun post(missed: List<Timer>) {
        val label = missed.singleOrNull()?.label?.takeIf { it.isNotBlank() }
        val contentIntent = PendingIntent.getActivity(
            context,
            AlarmNotifications.NOTIFICATION_ID_TIMER_MISSED,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, AlarmNotifications.CHANNEL_TIMER_MISSED)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(context.resources.getQuantityString(R.plurals.timer_missed_notice_title, missed.size, missed.size))
            .setContentText(label ?: context.resources.getQuantityString(R.plurals.timer_missed_notice_text, missed.size))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .build()
        manager?.notify(AlarmNotifications.NOTIFICATION_ID_TIMER_MISSED, notification)
    }

    override fun clear() {
        manager?.cancel(AlarmNotifications.NOTIFICATION_ID_TIMER_MISSED)
    }
}
