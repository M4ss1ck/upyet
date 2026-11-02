package dev.upyet.worldclock.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.upyet.R
import dev.upyet.core.ui.currentLocale
import dev.upyet.core.ui.rememberLocalized
import dev.upyet.timer.ui.ringTimePattern
import dev.upyet.worldclock.data.WorldCity
import dev.upyet.worldclock.domain.ClockChange
import dev.upyet.worldclock.domain.OffsetChange
import dev.upyet.worldclock.domain.WorldClockRow
import dev.upyet.worldclock.domain.offsetParts
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MINUS = "−"
private const val PLUS = "+"
private const val HOURS_ON_A_12_HOUR_FACE = 12

/** The city's name in [locale]: the Spanish name only for a Spanish locale that has one, the gazetteer name otherwise. */
internal fun cityName(city: WorldCity, locale: Locale): String =
    if (locale.language == "es" && city.nameEs.isNotBlank()) city.nameEs else city.name

/** The country's name in [locale]. The two-letter code is the fallback when the platform does not know the region. */
internal fun countryName(isoA2: String, locale: Locale): String {
    val region = try {
        Locale.Builder().setRegion(isoA2).build()
    } catch (e: java.util.IllformedLocaleException) {
        return isoA2
    }
    return region.getDisplayCountry(locale).ifBlank { isoA2 }
}

@Composable
internal fun placeTitle(city: WorldCity): String {
    val locale = currentLocale()
    return stringResource(R.string.world_clock_place_name, cityName(city, locale), countryName(city.isoA2, locale))
}

/** "+8 h 30 min", "−5 h", or "same time". The sign is a real minus, not a hyphen, so it reads as arithmetic. */
@Composable
internal fun offsetText(offsetMinutes: Int): String {
    val parts = offsetParts(offsetMinutes)
    return if (parts.sign == 0) {
        stringResource(R.string.world_clock_same_time)
    } else {
        amountText(offsetMinutes, if (parts.sign < 0) MINUS else PLUS)
    }
}

/** "8 h 30 min" after [sign], for a non-zero amount; the amount's own sign is ignored. */
@Composable
private fun amountText(minutes: Int, sign: String): String {
    val parts = offsetParts(minutes)
    return when {
        parts.minutes == 0 -> stringResource(R.string.world_clock_offset_hours, sign, parts.hours)
        parts.hours == 0 -> stringResource(R.string.world_clock_offset_minutes, sign, parts.minutes)
        else -> stringResource(R.string.world_clock_offset_hours_minutes, sign, parts.hours, parts.minutes)
    }
}

@Composable
internal fun dayLabel(dayDelta: Int): String = stringResource(
    when {
        dayDelta < 0 -> R.string.world_clock_yesterday
        dayDelta > 0 -> R.string.world_clock_tomorrow
        else -> R.string.world_clock_today
    },
)

/** "Tomorrow, +8 h 30 min": the second line of a place row. */
@Composable
internal fun dayAndOffset(row: WorldClockRow.Place): String =
    stringResource(R.string.world_clock_day_offset, dayLabel(row.dayDelta), offsetText(row.offsetMinutes))

/** The offset, with the day only when it is not today: a place on another day is the one fact the time alone cannot say. */
@Composable
private fun dayIfOtherAndOffset(row: WorldClockRow.Place): String =
    if (row.dayDelta == 0) offsetText(row.offsetMinutes) else dayAndOffset(row)

/** "+8 h (−1 h on Mar 28)": the second line of a list row. The parenthesis is a change to the offset, not to the place's clock. */
@Composable
internal fun placeOffsetLine(row: WorldClockRow.Place, change: OffsetChange?): String {
    val offset = dayIfOtherAndOffset(row)
    if (change == null) return offset
    return stringResource(R.string.world_clock_offset_with_change, offset, offsetText(change.deltaMinutes), datePart(change.date, "MMMd"))
}

/** What TalkBack reads for [placeOffsetLine]: the same facts, with the change spelled out instead of in symbols. */
@Composable
internal fun placeOffsetDescription(row: WorldClockRow.Place, change: OffsetChange?): String {
    val offset = dayIfOtherAndOffset(row)
    if (change == null) return offset
    return stringResource(
        R.string.world_clock_offset_change_description,
        offset,
        spokenChange(change.deltaMinutes),
        datePart(change.date, "MMMMd"),
    )
}

@Composable
private fun spokenChange(deltaMinutes: Int): String {
    val parts = offsetParts(deltaMinutes)
    val hours = pluralStringResource(R.plurals.world_clock_spoken_hours, parts.hours, parts.hours)
    val minutes = pluralStringResource(R.plurals.world_clock_spoken_minutes, parts.minutes, parts.minutes)
    val amount = when {
        parts.minutes == 0 -> hours
        parts.hours == 0 -> minutes
        else -> stringResource(R.string.world_clock_spoken_hours_minutes, hours, minutes)
    }
    return stringResource(if (parts.sign < 0) R.string.world_clock_spoken_minus else R.string.world_clock_spoken_plus, amount)
}

/** "Clocks go back 1 h on Sunday, October 31, at 03:00": the home time's line, in the phone's 12 or 24 hour style. */
@Composable
internal fun homeClockChangeText(change: ClockChange): String {
    val context = LocalContext.current
    val timeFormatter = rememberLocalized { DateTimeFormatter.ofPattern(ringTimePattern(context), it) }
    val time = change.before.toLocalTime()
    return pluralStringResource(
        if (change.deltaMinutes < 0) R.plurals.world_clock_home_change_back else R.plurals.world_clock_home_change_forward,
        displayedHour(time, DateFormat.is24HourFormat(context)),
        amountText(change.deltaMinutes, sign = ""),
        datePart(change.before.toLocalDate(), "EEEEMMMMd"),
        timeFormatter.format(time),
    )
}

/** The hour as the clock face reads it. The sentence agrees with it: Spanish says "a la 1:00" but "a las 2:00". */
internal fun displayedHour(time: LocalTime, is24Hour: Boolean): Int = when {
    is24Hour -> time.hour
    time.hour % HOURS_ON_A_12_HOUR_FACE == 0 -> HOURS_ON_A_12_HOUR_FACE
    else -> time.hour % HOURS_ON_A_12_HOUR_FACE
}

/** [date] by the locale's own arrangement of [skeleton], which is what puts "Mar 28" and "28 mar" the right way round. */
@Composable
private fun datePart(date: LocalDate, skeleton: String): String {
    val formatter = rememberLocalized { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(it, skeleton), it) }
    return formatter.format(date)
}

/** The ICU zone label, only when it adds something the city name does not already say. */
@Composable
internal fun zoneLabelText(row: WorldClockRow.Place, zoneLabel: String?): String? {
    val name = cityName(row.city, currentLocale())
    return zoneLabel?.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) }
        ?.let { stringResource(R.string.world_clock_zone_label, it) }
}
