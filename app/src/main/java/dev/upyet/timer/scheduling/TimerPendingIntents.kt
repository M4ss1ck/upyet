package dev.upyet.timer.scheduling

import android.content.Context
import android.content.Intent
import dev.upyet.timer.domain.TimerId
import java.time.Instant

/**
 * The fixed identity of the one timer alarm (ADR 0008, AGENTS.md §6a). Action, component and request code
 * never change, so arming again replaces the alarm instead of adding a second one; the extras say which
 * timer and which end instant it was armed for, which is what delivery validates.
 */
object TimerPendingIntents {
    const val ACTION_TIMER_EXPIRED = "dev.upyet.action.TIMER_EXPIRED"
    const val EXTRA_TIMER_ID = "timer_id"
    const val EXTRA_ENDS_AT = "ends_at"

    /** One alarm, one request code. Alarm request codes live under a different action, so they cannot collide. */
    const val REQUEST_CODE = 0

    fun expiryIntent(context: Context, alarm: TimerAlarm?): Intent = Intent(context, TimerReceiver::class.java).apply {
        action = ACTION_TIMER_EXPIRED
        if (alarm != null) {
            putExtra(EXTRA_TIMER_ID, alarm.timerId.value)
            putExtra(EXTRA_ENDS_AT, alarm.endsAt.toEpochMilli())
        }
    }

    /** The timer and end instant a delivery was armed for, or null when the intent is not a well-formed one. */
    fun delivered(intent: Intent): Pair<TimerId, Instant>? {
        val id = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
        val endsAt = intent.getLongExtra(EXTRA_ENDS_AT, -1L)
        if (id < 0 || endsAt < 0) return null
        return TimerId(id) to Instant.ofEpochMilli(endsAt)
    }
}
