package dev.upyet.timer.ui

import java.time.Duration

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L

/** "1:05:09" from an hour up, "5:09" below it. Whole seconds (truncated), never negative. */
fun formatTimerDuration(d: Duration): String {
    val total = d.seconds.coerceAtLeast(0)
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return if (hours > 0) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
}

private fun two(n: Long): String = n.toString().padStart(2, '0')
