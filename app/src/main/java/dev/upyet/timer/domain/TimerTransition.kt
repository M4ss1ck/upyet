package dev.upyet.timer.domain

/** The result of applying one event of ADR 0008's transition table to a [Timer]. */
sealed interface TimerTransition {
    /** The timer the caller persists. Leaving [TimerRunState.Ringing] stops the ringing, leaving [TimerRunState.Missed] clears its notification. */
    data class Applied(val timer: Timer) : TimerTransition

    /** The cell says no-op, ignored or unchanged: nothing to persist and nothing to re-arm. */
    data object NoOp : TimerTransition

    /** The cell says not offered: the UI shows no control for it, and a caller that asks anyway changes nothing. */
    data object NotOffered : TimerTransition

    /** The timer is gone. If it was ringing the ringing stops; if it was missed its notification is cleared. */
    data object Removed : TimerTransition
}
