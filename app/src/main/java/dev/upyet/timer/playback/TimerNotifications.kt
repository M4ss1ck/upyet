package dev.upyet.timer.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.R
import dev.upyet.core.notifications.AlarmNotifications
import dev.upyet.timer.ringing.TimerRingingActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one timer ringing notification: ADR 0012's `timer_ringing` channel, id 1101, ongoing, `CATEGORY_ALARM`,
 * `PRIORITY_HIGH`, with a full-screen intent and a content intent to the ringing screen. One notification
 * serves every ringing timer and no group is used (ADR 0012). The heads-up actions follow [TimerHeadsUp].
 * Touches no Room: it is built before first unlock too.
 */
@Singleton
class TimerNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    fun build(ringing: List<RingingTimer>): Notification {
        val screen = PendingIntent.getActivity(
            context,
            AlarmNotifications.NOTIFICATION_ID_TIMER_RINGING,
            screenIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val headsUp = TimerHeadsUp.of(ringing)
        val resources = context.resources
        val count = ringing.size
        val single = ringing.singleOrNull()
        // Empty only for the instant between startForeground and the first read: no count is better than a false one.
        val title = when {
            ringing.isEmpty() -> context.getString(R.string.timer_ringing_title)
            single == null -> resources.getQuantityString(R.plurals.timer_ringing_count, count, count)
            single.label.isBlank() -> context.getString(R.string.timer_ringing_title)
            else -> context.getString(R.string.timer_ringing_title_labelled, single.label)
        }
        val text = when (headsUp) {
            TimerHeadsUp.TapToOpen -> context.getString(R.string.timer_ringing_tap_to_open)
            else -> resources.getQuantityString(R.plurals.timer_ringing_count, count, count)
        }
        val builder = NotificationCompat.Builder(context, AlarmNotifications.CHANNEL_TIMER_RINGING)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
        when (headsUp) {
            TimerHeadsUp.TapToOpen -> Unit

            is TimerHeadsUp.StopOrAddMinute -> {
                builder.addAction(
                    0,
                    context.getString(R.string.timer_notification_stop),
                    command(TimerPlaybackService.stopIntent(context, headsUp.timerId), REQUEST_STOP),
                )
                builder.addAction(
                    0,
                    context.getString(R.string.timer_notification_add_minute),
                    command(TimerPlaybackService.addMinuteIntent(context, headsUp.timerId), REQUEST_ADD_MINUTE),
                )
            }

            TimerHeadsUp.StopAll -> builder.addAction(
                0,
                context.getString(R.string.timer_notification_stop_all),
                command(TimerPlaybackService.stopAllIntent(context), REQUEST_STOP_ALL),
            )
        }
        return builder.build()
    }

    /** The timer ringing screen; it shows over the keyguard and never dismisses it (AGENTS.md §7). */
    private fun screenIntent(): Intent = Intent(context, TimerRingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Only one heads-up variant shows at a time, so one request code per action is enough: the extras of the
     * current timer replace the previous ones (FLAG_UPDATE_CURRENT). The service is already in the foreground.
     */
    private fun command(intent: Intent, requestCode: Int): PendingIntent = PendingIntent.getForegroundService(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal companion object {
        // Inside the clock-tool block (ADR 0012), after the four notification ids, so nothing collides with 1001-1004.
        const val REQUEST_STOP = 1105
        const val REQUEST_ADD_MINUTE = 1106
        const val REQUEST_STOP_ALL = 1107
    }
}
