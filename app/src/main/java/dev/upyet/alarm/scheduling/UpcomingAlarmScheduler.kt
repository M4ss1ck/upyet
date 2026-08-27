package dev.upyet.alarm.scheduling

import dev.upyet.alarm.domain.UpcomingAlarm
import java.time.Instant

/**
 * Owns the single upcoming-alarm shade notification and the inexact alarm that posts it.
 *
 * Deliberately separate from the alarm-critical path: an upcoming alarm never rings, never creates an
 * occurrence, and never enters the Direct Boot mirror. Do not add a constant to [AlarmOccurrenceKind]
 * for it, and do not promote its scheduling to `setAlarmClock` - exact alarms are reserved for real
 * occurrences.
 */
interface UpcomingAlarmScheduler {
    /** Replaces whatever was scheduled or posted before: at most one exists globally. */
    fun sync(upcoming: UpcomingAlarm?, now: Instant)
    fun cancelNotification()
}
