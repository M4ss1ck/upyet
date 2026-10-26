package dev.upyet.timer.scheduling

import javax.inject.Inject

/**
 * Re-arms the one exact alarm for the earliest-ending timer (AGENTS.md §6a). Launch housekeeping calls it
 * because a force stop clears that alarm exactly as it clears alarms, and the platform announces nothing.
 * Implementations must be idempotent: every launch calls it.
 */
fun interface TimerRearm {
    suspend fun rearm()
}

/** Bound until the timer scheduler exists (slice #35); there are no timers to re-arm yet. */
class NoOpTimerRearm @Inject constructor() : TimerRearm {
    override suspend fun rearm() = Unit
}
