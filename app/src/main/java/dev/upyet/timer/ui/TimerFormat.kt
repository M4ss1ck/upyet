package dev.upyet.timer.ui

import java.time.Duration
import java.util.Locale

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L

/**
 * "1:05:09" from an hour up, "5:09" below it. Whole seconds (truncated), never negative. The digits are the
 * locale's (AGENTS.md §13); the colon layout is the countdown convention every clock app uses.
 */
fun formatTimerDuration(d: Duration, locale: Locale = Locale.getDefault()): String {
    val total = d.seconds.coerceAtLeast(0)
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return if (hours >
        0
    ) {
        String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(locale, "%d:%02d", minutes, seconds)
    }
}
