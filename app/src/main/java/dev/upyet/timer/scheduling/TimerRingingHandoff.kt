package dev.upyet.timer.scheduling

import dev.upyet.timer.domain.TimerId

/**
 * Where a validated expiry goes once the timer is stored as Ringing. Only `TimerPlaybackService` may ring
 * (AGENTS.md §8a, §20); `ServiceTimerRingingHandoff` is the binding, and tests substitute a recorder.
 */
fun interface TimerRingingHandoff {
    fun ring(timerId: TimerId)
}
