package dev.upyet.timer.scheduling

import android.content.Intent

/**
 * What [TimerReceiver] does with each broadcast it is registered for. A zone change is [ZONE_CHANGED]: both timer
 * anchors are zone-free, so it re-arms nothing and only re-posts the running notice (AGENTS.md §6a). `USER_UNLOCKED`
 * is absent: `UserUnlockedReceiver` runs timer discovery right after it reconciles the mirror into Room.
 */
enum class TimerReceiverCommand {
    /** The one exact alarm fired: validate it against the timer's current end, then ring or drop it. */
    EXPIRY,

    /** Elapsed realtime restarted and every alarm is gone: discovery measured on the wall clock. */
    BOOT,

    /** Nothing about the timers changed, but the alarm may be gone or newly allowed: re-arm. */
    REARM,

    /** `TIME_SET`: re-anchor running timers from the elapsed delta; the armed alarm does not move. */
    WALL_CLOCK_CHANGED,

    /** `TIMEZONE_CHANGED`: re-post the running notice so its ring time reads in the new zone. Nothing re-arms. */
    ZONE_CHANGED,

    /** The running-timer notification's Pause action (ADR 0012): a Running timer is frozen. */
    PAUSE,

    /** The running-timer notification's +1 min action: a Running timer gains a minute. */
    ADD_MINUTE,

    /** The running-timer notification's Reset action: a Running timer goes back to Idle at its full length. */
    RESET,
    ;

    companion object {
        /** `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, spelled out: it is API 31 and minSdk is 26. */
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        fun of(action: String?): TimerReceiverCommand? = when (action) {
            TimerPendingIntents.ACTION_TIMER_EXPIRED -> EXPIRY
            Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED -> BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED, ACTION_EXACT_ALARM_PERMISSION_CHANGED -> REARM
            Intent.ACTION_TIME_CHANGED -> WALL_CLOCK_CHANGED
            Intent.ACTION_TIMEZONE_CHANGED -> ZONE_CHANGED
            TimerRunningActions.ACTION_PAUSE -> PAUSE
            TimerRunningActions.ACTION_ADD_MINUTE -> ADD_MINUTE
            TimerRunningActions.ACTION_RESET -> RESET
            else -> null
        }
    }
}
