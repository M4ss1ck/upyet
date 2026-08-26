package dev.upyet.core.directboot

import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.Recurrence
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private const val MINUTES_PER_HOUR = 60

/** Recurrence encoding used by the device-protected mirror; kept independent of Room's own encoding. */
object MirroredRecurrence {
    const val ONE_TIME = "ONE_TIME"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"

    fun encode(recurrence: Recurrence): Pair<String, Int> = when (recurrence) {
        Recurrence.OneTime -> ONE_TIME to 0
        Recurrence.Daily -> DAILY to 0
        is Recurrence.Weekly -> WEEKLY to recurrence.days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
    }

    fun decode(type: String, weekdayMask: Int): Recurrence = when (type) {
        DAILY -> Recurrence.Daily
        WEEKLY -> Recurrence.Weekly(DayOfWeek.entries.filter { weekdayMask and (1 shl (it.value - 1)) != 0 }.toSet())
        else -> Recurrence.OneTime
    }
}

/**
 * Reconstructs the alarm-critical parts of an [Alarm] from the device-protected mirror so the alarm can
 * ring, snooze and reschedule before first unlock. The label is intentionally empty: user content is never
 * mirrored to device-protected storage.
 */
fun MirroredAlarm.toAlarm(): Alarm = Alarm(
    id = alarmId,
    time = LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR),
    enabled = true,
    label = "",
    recurrence = MirroredRecurrence.decode(recurrenceType, weekdayMask),
    soundUri = soundUri,
    vibrationEnabled = vibrationEnabled,
    snoozeMinutes = snoozeMinutes,
    evidenceEnabled = false,
    skipNextOn = skipNextOnEpochDay?.let(LocalDate::ofEpochDay),
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
