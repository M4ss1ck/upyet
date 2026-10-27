package dev.upyet.stopwatch.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.MainActivity
import dev.upyet.R
import dev.upyet.core.notifications.AlarmNotifications
import dev.upyet.stopwatch.domain.StopwatchNotices
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The running-stopwatch notice on ADR 0012's `clock_tool_ongoing` channel, id 1104: a silent chronometer counting
 * up from [show]'s start instant. It is ongoing, so it stays until [cancel]. It has no actions, because the
 * stopwatch is controlled from its screen, and it has no service and no wake lock: the platform draws the count
 * (AGENTS.md §5a). It is never grouped, which is why it does not call `setSilent` either (see the timer's
 * running notice for the same reasoning).
 */
@Singleton
class AndroidStopwatchNotices @Inject constructor(@ApplicationContext private val context: Context) : StopwatchNotices {
    private val manager: NotificationManager? get() = context.getSystemService(NotificationManager::class.java)

    override fun show(startedAt: Instant) {
        val builder = NotificationCompat.Builder(context, AlarmNotifications.CHANNEL_CLOCK_TOOL_ONGOING)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(context.getString(R.string.stopwatch))
            .setContentText(context.getString(R.string.stopwatch_notification_running))
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(startedAt.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(contentIntent())
        manager?.notify(AlarmNotifications.NOTIFICATION_ID_STOPWATCH, builder.build())
    }

    override fun cancel() {
        manager?.cancel(AlarmNotifications.NOTIFICATION_ID_STOPWATCH)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        AlarmNotifications.NOTIFICATION_ID_STOPWATCH,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
