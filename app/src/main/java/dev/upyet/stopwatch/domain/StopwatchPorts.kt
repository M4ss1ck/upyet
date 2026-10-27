package dev.upyet.stopwatch.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Persists the stopwatch so it survives process death. Credential-protected: the stopwatch has no Direct Boot path. */
interface StopwatchStore {
    val stopwatch: Flow<Stopwatch>

    /** Applies [transform] atomically to the stored value and returns the new value. */
    suspend fun update(transform: (Stopwatch) -> Stopwatch): Stopwatch
}

/** The device's boot count, or null where the platform does not report one. */
fun interface BootCount {
    fun current(): Int?
}

/**
 * The ongoing stopwatch notice on ADR 0012's `clock_tool_ongoing` channel, id 1104. Shown only while running; the
 * platform draws the count up from [startedAt], so nothing ticks (AGENTS.md §5a).
 */
interface StopwatchNotices {
    fun show(startedAt: Instant)

    fun cancel()
}
