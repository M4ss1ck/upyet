package dev.upyet.worldclock.domain

import dev.upyet.worldclock.data.WorldCity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sign

/** One line of the World clock list. Rows keep the order of the persisted ids. */
sealed interface WorldClockRow {
    val geonamesId: Long

    /** A resolved place: its wall-clock time now, the calendar-day difference and the UTC-offset difference from home. */
    data class Place(val city: WorldCity, val localTime: LocalDateTime, val dayDelta: Int, val offsetMinutes: Int) : WorldClockRow {
        override val geonamesId: Long get() = city.geonamesId
    }

    /** A persisted id the loaded gazetteer no longer resolves. Kept in place, never deleted silently (issue #45). */
    data class Unavailable(override val geonamesId: Long) : WorldClockRow
}

/**
 * Resolves persisted ids into rows at [now]. One row per input id, in input order.
 *
 * Differences are measured at one instant, not per city: the home and the place are compared by the offsets in
 * force now, so a daylight-saving switch in one zone moves its row, which is the number a traveller reads.
 */
fun worldClockRows(ids: List<Long>, citiesById: Map<Long, WorldCity>, now: Instant, homeZone: ZoneId): List<WorldClockRow> {
    val homeNow = now.atZone(homeZone)
    val homeOffsetSeconds = homeNow.offset.totalSeconds
    return ids.map { id ->
        val city = citiesById[id]
        if (city == null) {
            WorldClockRow.Unavailable(id)
        } else {
            val placeNow = now.atZone(city.zoneId)
            WorldClockRow.Place(
                city = city,
                localTime = placeNow.toLocalDateTime(),
                dayDelta = ChronoUnit.DAYS.between(homeNow.toLocalDate(), placeNow.toLocalDate()).toInt(),
                offsetMinutes = (placeNow.offset.totalSeconds - homeOffsetSeconds) / 60,
            )
        }
    }
}

/**
 * The most populous gazetteer place in [homeZone], used as the home marker; null when the zone has none.
 *
 * Population ties break on the lower GeoNames id so the marker never flips between launches.
 */
fun homeCity(cities: List<WorldCity>, homeZone: ZoneId): WorldCity? = cities
    .filter { it.zoneId == homeZone }
    .minWithOrNull(compareByDescending<WorldCity> { it.population }.thenBy { it.geonamesId })

/** Splits an offset difference for display: sign (-1, 0, 1), whole hours, remaining minutes (both non-negative). */
data class OffsetParts(val sign: Int, val hours: Int, val minutes: Int)

fun offsetParts(offsetMinutes: Int): OffsetParts {
    val magnitude = abs(offsetMinutes)
    return OffsetParts(
        sign = offsetMinutes.sign,
        hours = magnitude / MINUTES_PER_HOUR,
        minutes = magnitude % MINUTES_PER_HOUR,
    )
}

private const val MINUTES_PER_HOUR = 60
