package dev.upyet.timer.scheduling

import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerRunState

/**
 * What the one missed-timer notice (`timer_missed`, id 1103; ADR 0012) does after timers change. It appears
 * when discovery moves a timer to Missed, follows the missed set while it shows, and goes once Reset or delete
 * leaves no timer Missed. It is not ongoing, so the user may swipe it away, and an unrelated change must not
 * bring it back: only a newly missed timer posts it when it is not showing.
 */
sealed interface MissedNotice {
    data class Post(val missed: List<Timer>) : MissedNotice

    data object Clear : MissedNotice

    data object Leave : MissedNotice

    companion object {
        fun decide(timers: List<Timer>, newlyMissed: Boolean, showing: Boolean): MissedNotice {
            val missed = timers.filter { it.runState == TimerRunState.Missed }
            return when {
                missed.isEmpty() -> if (showing) Clear else Leave
                newlyMissed || showing -> Post(missed)
                else -> Leave
            }
        }
    }
}
