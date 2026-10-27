package dev.upyet.intents

import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.scheduling.SchedulingResult
import java.time.Instant

/** The alarm mutations an intent may make, each the same one the Alarms tab makes for the same user action. */
interface AlarmActions {
    /** Programs [alarm]'s next occurrence, exactly as saving it from the editor does. */
    suspend fun schedule(alarm: Alarm): SchedulingResult

    /** Skip next on a recurring alarm; null when it has no occurrence that can be skipped. */
    suspend fun skipNext(alarm: Alarm): SchedulingResult?

    /** Turns the alarm off indefinitely, clearing any skip and cancelling its main and snooze alarms. */
    suspend fun turnOff(alarm: Alarm)
}

/**
 * The alarms waiting on a snooze, with the instant each will ring again. A snoozed alarm is part of a wake-up in
 * progress, so `DISMISS_ALARM` refuses it (ADR 0007).
 */
fun interface SnoozedAlarms {
    fun snoozed(now: Instant): Map<AlarmId, Instant>
}
