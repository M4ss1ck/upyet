package dev.upyet.timer.scheduling

import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerTransition

/** Every user edit to a timer (Timer tab, running-timer notification), serialised with expiry and discovery. */
interface TimerEditor {
    /** Stores a new timer and re-arms; only ever called unlocked, from the UI. Returns the assigned id. */
    suspend fun add(timer: Timer): TimerId

    /**
     * Applies [transition] to timer [id] when it exists, is not Ringing, and [onlyIf] holds. Applied → save +
     * re-arm; Removed → delete + re-arm (Room only: before first unlock a delete is refused and logged).
     * Anything else changes nothing. Returns the transition, or NoOp when refused.
     */
    suspend fun edit(
        id: TimerId,
        command: String,
        onlyIf: (Timer) -> Boolean = { true },
        transition: (Timer, TimerAnchor) -> TimerTransition,
    ): TimerTransition
}
