package dev.upyet.alarm.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The skip-next rules, pure and Android-free so the alarm list, the skip action and the upcoming-alarm
 * notification all agree on whether an alarm is skipped.
 *
 * The stored skip is only a date. It lapses when the occurrence it suppressed would have rung - the date at
 * the alarm's time in the current zone - not at the end of that day. Nothing runs at that moment to clear
 * the stored date, so lapsing is derived from the clock rather than written.
 */
object SkipNext {
    /** Whether [alarm] is the kind that can skip: enabled and recurring. A one-time alarm skipped is an alarm turned off. */
    fun canSkip(alarm: Alarm): Boolean = alarm.enabled && alarm.recurrence != Recurrence.OneTime

    /** The date whose occurrence [alarm] is suppressing, or null when there is no skip or it has lapsed by [now]. */
    fun activeDate(alarm: Alarm, zone: ZoneId, now: Instant): LocalDate? = alarm.skipNextOn?.takeIf { date ->
        // ZonedDateTime.of resolves a DST gap or overlap the same way NextOccurrenceCalculator does, so the
        // skip lapses at exactly the instant the suppressed occurrence would have fired.
        ZonedDateTime.of(date, alarm.time, zone).toInstant() > now
    }

    /** The date a new skip would suppress: the next occurrence from [now], or null when [alarm] cannot skip. */
    fun target(alarm: Alarm, zone: ZoneId, now: Instant): LocalDate? {
        if (!canSkip(alarm)) return null
        // A lapsed skip lies in the past, so it can no longer filter a future candidate and is not passed on.
        return NextOccurrenceCalculator.next(alarm.time, alarm.recurrence, zone, now)?.atZone(zone)?.toLocalDate()
    }
}
