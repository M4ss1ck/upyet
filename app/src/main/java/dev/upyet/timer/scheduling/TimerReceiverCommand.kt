package dev.upyet.timer.scheduling

import android.content.Intent

/**
 * What [TimerReceiver] does with each broadcast it is registered for. `TIMEZONE_CHANGED` is deliberately
 * absent: both timer anchors are zone-free, so a zone change re-arms nothing (AGENTS.md §6a). `USER_UNLOCKED`
 * is absent too: `UserUnlockedReceiver` runs timer discovery right after it reconciles the mirror into Room.
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
    ;

    companion object {
        /** `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, spelled out: it is API 31 and minSdk is 26. */
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        fun of(action: String?): TimerReceiverCommand? = when (action) {
            TimerPendingIntents.ACTION_TIMER_EXPIRED -> EXPIRY
            Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED -> BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED, ACTION_EXACT_ALARM_PERMISSION_CHANGED -> REARM
            Intent.ACTION_TIME_CHANGED -> WALL_CLOCK_CHANGED
            else -> null
        }
    }
}
