package dev.upyet.timer.domain

import java.time.Duration

/** The five timer run states of ADR 0008, named after what the user sees (GLOSSARY.md: Timer run state). */
sealed interface TimerRunState {
    /** At its full length, waiting to be started. */
    data object Idle : TimerRunState

    /** Counting down: [RemainingTime] of [remainingAtAnchor] measured from [anchor]. */
    data class Running(val remainingAtAnchor: Duration, val anchor: TimerAnchor) : TimerRunState {
        init {
            require(!remainingAtAnchor.isNegative) { "remainingAtAnchor must not be negative" }
        }
    }

    /** Stopped counting with [remaining] left. */
    data class Paused(val remaining: Duration) : TimerRunState {
        init {
            require(!remaining.isNegative) { "remaining must not be negative" }
        }
    }

    /** Reached zero and sounding, until Stop, +1 min, Reset or delete. */
    data object Ringing : TimerRunState

    /** Discovered more than [Timer.MISSED_AFTER] past its end: it never rang and waits silently. */
    data object Missed : TimerRunState
}
