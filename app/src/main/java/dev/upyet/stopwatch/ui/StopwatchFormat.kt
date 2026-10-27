package dev.upyet.stopwatch.ui

import java.time.Duration
import java.util.Locale

private const val MILLIS_PER_CENTISECOND = 10L
private const val CENTISECONDS_PER_SECOND = 100L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/**
 * "MM:SS.cc" under one hour, "H:MM:SS.cc" from one hour up. Hundredths are truncated, never rounded, so a lap
 * shows the time that had actually passed. Negative input clamps to zero. The digits follow the locale, the same
 * way the Timer's countdown does (see `formatTimerDuration`).
 */
internal fun formatStopwatch(elapsed: Duration, locale: Locale = Locale.getDefault()): String {
    val centiseconds = elapsed.coerceAtLeast(Duration.ZERO).toMillis() / MILLIS_PER_CENTISECOND
    val hundredths = centiseconds % CENTISECONDS_PER_SECOND
    val totalSeconds = centiseconds / CENTISECONDS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(locale, "%d:%02d:%02d.%02d", hours, minutes, seconds, hundredths)
    } else {
        String.format(locale, "%02d:%02d.%02d", minutes, seconds, hundredths)
    }
}
