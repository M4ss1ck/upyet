package dev.myalarm.alarm.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Enough to render the next-alarm hero without the list screen re-reading the alarm itself. */
data class NextAlarmInfo(val firesAt: Instant, val time: LocalTime, val label: String, val evidenceEnabled: Boolean)

/** Picks which enabled alarm rings soonest - kept pure and Android-free so it is trivial to unit test. */
object NextAlarm {
    /**
     * Returns the enabled alarm that rings soonest from [from]. Disabled alarms are never
     * candidates, and an alarm whose next occurrence falls outside the calculator's search window
     * is skipped.
     */
    fun select(alarms: List<Alarm>, zone: ZoneId, from: Instant): NextAlarmInfo? = alarms
        .asSequence()
        .filter { it.enabled }
        .mapNotNull { alarm ->
            NextOccurrenceCalculator.next(alarm.time, alarm.recurrence, zone, from)?.let { firesAt ->
                NextAlarmInfo(firesAt, alarm.time, alarm.label, alarm.evidenceEnabled)
            }
        }.minByOrNull { it.firesAt }
}
