package dev.upyet.worldclock.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.upyet.R
import dev.upyet.core.ui.currentLocale
import dev.upyet.worldclock.data.WorldCity
import dev.upyet.worldclock.domain.WorldClockRow
import dev.upyet.worldclock.domain.offsetParts
import java.util.Locale

private const val MINUS = "−"
private const val PLUS = "+"

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
    val sign = if (parts.sign < 0) MINUS else PLUS
    return when {
        parts.sign == 0 -> stringResource(R.string.world_clock_same_time)
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

/** The ICU zone label, only when it adds something the city name does not already say. */
@Composable
internal fun zoneLabelText(row: WorldClockRow.Place, zoneLabel: String?): String? {
    val name = cityName(row.city, currentLocale())
    return zoneLabel?.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) }
        ?.let { stringResource(R.string.world_clock_zone_label, it) }
}
