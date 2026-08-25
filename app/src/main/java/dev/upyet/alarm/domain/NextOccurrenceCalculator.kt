package dev.upyet.alarm.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Calculates the next local-time occurrence without relying on fixed elapsed-day durations. */
object NextOccurrenceCalculator {
    private const val SEARCH_DAYS = 8L

    /**
     * Returns the first occurrence after [from], or at [from] when [inclusive] is true.
     *
     * Candidate local times use [ZonedDateTime].of: a daylight-saving gap shifts the time
     * forward by the gap duration, while an overlap uses the earlier offset.
     */
    fun next(time: LocalTime, recurrence: Recurrence, zone: ZoneId, from: Instant, inclusive: Boolean = false): Instant? {
        val localDate = from.atZone(zone).toLocalDate()
        val lastDate = localDate.plusDays(SEARCH_DAYS - 1)

        return generateSequence(localDate) { date ->
            if (date < lastDate) date.plusDays(1) else null
        }.filter { date -> isAllowed(date, recurrence) }
            .map { date ->
                ZonedDateTime
                    .of(
                        date,
                        time,
                        zone,
                    ).toInstant()
            }.firstOrNull { candidate ->
                candidate > from ||
                    (inclusive && candidate == from)
            }
    }

    private fun isAllowed(date: LocalDate, recurrence: Recurrence): Boolean = when (recurrence) {
        Recurrence.OneTime -> {
            true
        }

        Recurrence.Daily -> {
            true
        }

        is Recurrence.Weekly -> {
            date.dayOfWeek in
                recurrence.days
        }
    }
}
