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
import dev.upyet.timer.domain.TimerId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The running-timer notice on ADR 0012's `clock_tool_ongoing` channel, id 1102: a silent chronometer counting
 * down to the timer that ends first, with Pause, +1 min and Reset. It is ongoing, so it stays until [cancel].
 * Its countdown is drawn from the anchored end instant, so nothing here counts time, and it has no service and
 * no wake lock (AGENTS.md §5a). It touches no Room, because discovery posts it before first unlock too, and it
 * never cancels the upcoming-alarm notice: a running timer says nothing about the next alarm. It is never
 * grouped, so the platform cannot fold it into another notice; that is also why it does not call `setSilent`,
 * whose compat implementation sets a group key. The low-importance channel keeps it quiet.
 */
@Singleton
class AndroidTimerRunningNotices @Inject constructor(@ApplicationContext private val context: Context) : TimerRunningNotices {
    private val manager: NotificationManager? get() = context.getSystemService(NotificationManager::class.java)

    override fun show(notice: RunningNotice.Show) {
        val id = notice.timer.id
        val label = notice.timer.label.takeIf { it.isNotBlank() } ?: context.getString(R.string.timer_running_title_untitled)
        val builder = NotificationCompat.Builder(context, AlarmNotifications.CHANNEL_CLOCK_TOOL_ONGOING)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(label)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(notice.endsAt.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(contentIntent())
        if (notice.alsoRunning > 0) {
            val others = notice.alsoRunning
            builder.setContentText(context.resources.getQuantityString(R.plurals.timer_running_others, others, others))
        }
        builder
            .addAction(
                0,
                context.getString(R.string.timer_notification_pause),
                action(TimerRunningActions.ACTION_PAUSE, TimerRunningActions.REQUEST_CODE_PAUSE, id),
            )
            .addAction(
                0,
                context.getString(R.string.timer_notification_add_minute),
                action(TimerRunningActions.ACTION_ADD_MINUTE, TimerRunningActions.REQUEST_CODE_ADD_MINUTE, id),
            )
            .addAction(
                0,
                context.getString(R.string.timer_notification_reset),
                action(TimerRunningActions.ACTION_RESET, TimerRunningActions.REQUEST_CODE_RESET, id),
            )
        manager?.notify(AlarmNotifications.NOTIFICATION_ID_TIMER_RUNNING, builder.build())
    }

    override fun cancel() {
        manager?.cancel(AlarmNotifications.NOTIFICATION_ID_TIMER_RUNNING)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        AlarmNotifications.NOTIFICATION_ID_TIMER_RUNNING,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Distinct request codes per action, so the three PendingIntents never replace one another. */
    private fun action(action: String, requestCode: Int, id: TimerId): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        TimerRunningActions.intent(context, action, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
