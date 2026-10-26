package dev.upyet.timer.playback

import dev.upyet.timer.domain.TimerId

/**
 * What the timer ringing heads-up offers while the phone is in use (issue #14, variant B). A ring with
 * **Timer evidence** on is ended only from the ringing screen, as an alarm is (AGENTS.md §7, ADR 0010), so
 * when any ringing timer records, or might (its details could not be read), the notification offers nothing that
 * ends a ring.
 */
sealed interface TimerHeadsUp {
    /** No actions: the body tap opens the ringing screen. */
    data object TapToOpen : TimerHeadsUp

    /** Exactly one timer rings and it does not record. */
    data class StopOrAddMinute(val timerId: TimerId) : TimerHeadsUp

    /** Two or more ring and none records. */
    data object StopAll : TimerHeadsUp

    companion object {
        fun of(ringing: List<RingingTimer>): TimerHeadsUp = when {
            ringing.isEmpty() || ringing.any { it.evidenceEnabled || it.detailsUnreadable } -> TapToOpen
            ringing.size == 1 -> StopOrAddMinute(ringing.single().id)
            else -> StopAll
        }
    }
}
