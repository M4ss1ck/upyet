package dev.upyet.alarm.domain

import java.time.Instant

data class UpcomingAlarm(val alarmId: AlarmId, val label: String, val triggerAt: Instant, val postAt: Instant, val canSkip: Boolean)

object UpcomingAlarmSelector {
    /** candidates: enabled alarms paired with their already-computed next trigger. */
    fun select(candidates: List<Pair<Alarm, Instant>>, leadMinutes: Int): UpcomingAlarm? {
        if (leadMinutes <= 0 || candidates.isEmpty()) return null
        val earliest = candidates.minByOrNull { it.second } ?: return null
        val triggerAt = earliest.second
        val alarm = earliest.first
        val postAt = triggerAt.minusSeconds(leadMinutes.toLong() * 60L)
        val canSkip = alarm.recurrence != Recurrence.OneTime
        return UpcomingAlarm(alarm.id, alarm.label, triggerAt, postAt, canSkip)
    }
}
