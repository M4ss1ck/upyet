package dev.upyet.worldclock.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.zone.ZoneOffsetTransition
import java.time.zone.ZoneRules

/** The next change to a place's offset from home: how far it moves, signed, and the day it moves. */
data class OffsetChange(val deltaMinutes: Int, val date: LocalDate)

/** A zone's next clock change: how far its clocks move, signed, and the wall-clock moment they move from. */
data class ClockChange(val deltaMinutes: Int, val before: LocalDateTime)

/**
 * The next clock change in either zone that alters the offset between them, within the next [HORIZON_MONTHS] months.
 *
 * Zones changing together are netted, and a pair that cancels out is skipped: Madrid and Paris never differ, so
 * without the horizon this would walk their rules forever. The date belongs to the zone whose clock changes, the
 * day anyone would find if they looked the change up; when both change at once it is the home date.
 */
fun nextOffsetChange(placeRules: ZoneRules, homeRules: ZoneRules, now: Instant): OffsetChange? {
    val horizon = horizonOf(now)
    var cursor = now
    while (true) {
        val placeNext = placeRules.nextTransition(cursor)
        val homeNext = homeRules.nextTransition(cursor)
        val soonest = listOfNotNull(homeNext, placeNext).minByOrNull { it.instant } ?: return null
        if (!soonest.instant.isBefore(horizon)) return null
        val place = placeNext?.takeIf { it.instant == soonest.instant }
        val home = homeNext?.takeIf { it.instant == soonest.instant }
        val delta = place.deltaMinutes() - home.deltaMinutes()
        // minByOrNull keeps the first of equal instants, so a shared moment is dated by home.
        if (delta != 0) return OffsetChange(delta, soonest.dateTimeBefore.toLocalDate())
        cursor = soonest.instant
    }
}

/** The zone's own next clock change within the next [HORIZON_MONTHS] months. */
fun nextClockChange(rules: ZoneRules, now: Instant): ClockChange? = rules.nextTransition(now)
    ?.takeIf { it.instant.isBefore(horizonOf(now)) }
    ?.let { ClockChange(it.deltaMinutes(), it.dateTimeBefore) }

private fun ZoneOffsetTransition?.deltaMinutes(): Int = if (this == null) 0 else duration.toMinutes().toInt()

private fun horizonOf(now: Instant): Instant = now.atOffset(ZoneOffset.UTC).plusMonths(HORIZON_MONTHS).toInstant()

/** Keeps a change's date unambiguous without a year, and bounds the walk over a pair that always cancels out. */
private const val HORIZON_MONTHS = 12L
