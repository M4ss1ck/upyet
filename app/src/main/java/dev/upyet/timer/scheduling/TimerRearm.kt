package dev.upyet.timer.scheduling

/**
 * Re-arms the one exact alarm for the earliest-ending timer (AGENTS.md §6a). Launch housekeeping calls it
 * because a force stop clears that alarm exactly as it clears alarms, and the platform announces nothing.
 * Implementations must be idempotent: every launch calls it.
 */
fun interface TimerRearm {
    suspend fun rearm()
}
