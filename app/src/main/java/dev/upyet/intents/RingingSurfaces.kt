package dev.upyet.intents

import android.app.ActivityOptions
import android.app.PendingIntent
import android.os.Build
import androidx.annotation.RequiresApi
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.notifications.AlarmNotifications
import dev.upyet.timer.playback.TimerNotifications
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings a ringing screen forward the only way AGENTS.md §7 allows: by sending the PendingIntent of its own ringing
 * notification (ADR 0007). Never a `startActivity()` to `RingingActivity` or `TimerRingingActivity`.
 *
 * The sender is the visible trampoline activity, so the platform allows the start; the explicit background-start
 * mode only matters on the releases that restrict PendingIntent senders. The device stays locked: the ringing
 * activities show over the keyguard and never dismiss it.
 */
@Singleton
class RingingSurfaces @Inject constructor(
    private val alarmNotifications: AlarmNotifications,
    private val timerNotifications: TimerNotifications,
) {
    fun bringAlarmForward() = send(alarmNotifications.ringingScreenIntent(), "alarm")

    fun bringTimerForward() = send(timerNotifications.ringingScreenIntent(), "timer")

    /** API 36 renames the permissive mode; the visible-sender variant is the narrowest one that still starts the screen. */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun visibleSenderOptions() = ActivityOptions.makeBasic()
        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
        .toBundle()

    /**
     * API 34 and 35 have only the plain "allowed" mode; API 36 deprecates it for
     * [ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE], which does not exist below 36, so the
     * deprecated constant is the only choice on this branch.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Suppress("DEPRECATION")
    private fun allowedStartOptions() = ActivityOptions.makeBasic()
        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        .toBundle()

    private fun send(intent: PendingIntent, surface: String) {
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA -> intent.send(visibleSenderOptions())
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> intent.send(allowedStartOptions())
                else -> intent.send()
            }
        } catch (_: PendingIntent.CanceledException) {
            // The ringing notification is gone, so nothing is ringing any more; there is nothing left to bring forward.
            AlarmLog.event("ringing_surface_send_failed", "surface" to surface)
        }
    }
}
