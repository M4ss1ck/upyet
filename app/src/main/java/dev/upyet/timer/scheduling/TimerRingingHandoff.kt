package dev.upyet.timer.scheduling

import dev.upyet.core.logging.AlarmLog
import dev.upyet.timer.domain.TimerId
import javax.inject.Inject

/**
 * Where a validated expiry goes once the timer is stored as Ringing. Only `TimerPlaybackService` may ring
 * (AGENTS.md §8a, §20), and it lands in slice #37; this is the seam it plugs into.
 */
fun interface TimerRingingHandoff {
    fun ring(timerId: TimerId)
}

/** Bound until `TimerPlaybackService` exists (slice #37): the timer is stored as Ringing, and nothing sounds yet. */
class PendingTimerRingingHandoff @Inject constructor() : TimerRingingHandoff {
    override fun ring(timerId: TimerId) {
        AlarmLog.event("timer_ring_handoff_pending", "timerId" to timerId.value)
    }
}
