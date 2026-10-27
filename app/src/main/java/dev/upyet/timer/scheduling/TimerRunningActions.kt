package dev.upyet.timer.scheduling

import android.content.Context
import android.content.Intent
import dev.upyet.timer.domain.TimerId

/**
 * The running-timer notification's three actions (ADR 0012): Pause, +1 min and Reset. Explicit broadcasts to
 * [TimerReceiver], which applies them through [TimerDiscovery] under its lock and only while the timer is
 * still Running, so a stale tap cannot touch a timer that has since paused, rung or been reset.
 */
object TimerRunningActions {
    const val ACTION_PAUSE = "dev.upyet.action.TIMER_PAUSE"
    const val ACTION_ADD_MINUTE = "dev.upyet.action.TIMER_ADD_MINUTE"
    const val ACTION_RESET = "dev.upyet.action.TIMER_RESET"

    /** Distinct from [TimerPendingIntents.REQUEST_CODE]; the actions differ too, so neither can replace the other. */
    const val REQUEST_CODE_PAUSE = 1
    const val REQUEST_CODE_ADD_MINUTE = 2
    const val REQUEST_CODE_RESET = 3

    fun intent(context: Context, action: String, id: TimerId): Intent = Intent(context, TimerReceiver::class.java)
        .setAction(action)
        .putExtra(TimerPendingIntents.EXTRA_TIMER_ID, id.value)

    fun timerId(intent: Intent): TimerId? = intent.getLongExtra(TimerPendingIntents.EXTRA_TIMER_ID, -1L).takeIf { it >= 0 }?.let(::TimerId)
}
