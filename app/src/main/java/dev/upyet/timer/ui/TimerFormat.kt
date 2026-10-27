package dev.upyet.timer.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

/**
 * The wall-clock ring time, "07:42" or "7:42 AM", in [zone] with [pattern] (a `DateTimeFormatter` pattern, see
 * [ringTimePattern]). Pure, so the zone and the pattern are both pinned by a JVM test.
 */
fun formatRingTime(endsAt: Instant, zone: ZoneId, pattern: String, locale: Locale): String =
    endsAt.atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))

/**
 * The pattern the device's clock style asks for, in the app's locale. It follows the 12 or 24 hour setting, because
 * the ring time is read against the device's clock, and [DateFormat.getBestDateTimePattern] lets the locale choose
 * the separator and the day period (the same approach as the clock intent's toast).
 */
fun ringTimePattern(context: Context): String {
    val locale = context.resources.configuration.locales[0]
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    return DateFormat.getBestDateTimePattern(locale, skeleton)
}
