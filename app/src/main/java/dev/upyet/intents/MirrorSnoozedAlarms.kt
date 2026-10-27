package dev.upyet.intents

import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import dev.upyet.core.directboot.AlarmMirror
import java.time.Instant
import javax.inject.Inject

/**
 * Reads the snoozed alarms from the device-protected mirror, which is the only store that knows about a snooze
 * and is safe to read before first unlock. A snooze record lingers after it fires with a past trigger, so only
 * records whose trigger is still in the future count as snoozed.
 */
class MirrorSnoozedAlarms @Inject constructor(private val mirror: AlarmMirror) : SnoozedAlarms {
    override fun snoozed(now: Instant): Map<AlarmId, Instant> =
        mirror.all().filter { it.kind == AlarmOccurrenceKind.SNOOZE && it.triggerAt > now }.associate { it.alarmId to it.triggerAt }
}
