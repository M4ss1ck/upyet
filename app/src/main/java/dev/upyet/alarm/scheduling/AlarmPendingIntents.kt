package dev.upyet.alarm.scheduling

import android.content.Context
import android.content.Intent
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import java.time.Instant

object AlarmPendingIntents {
    const val ACTION_ALARM = "dev.upyet.action.ALARM"
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_SCHEDULED_FOR = "scheduled_for"
    const val EXTRA_KIND = "kind"
    const val EXTRA_PARENT_OCCURRENCE_ID = "parent_occurrence_id"
    const val EXTRA_SNOOZES_REMAINING = "snoozes_remaining"

    /** Room ids stay far below 2^30, so the shift cannot collide; identity is stable across restarts. */
    fun requestCode(alarmId: AlarmId, kind: AlarmOccurrenceKind): Int = (alarmId.value.toInt() shl 1) or kind.requestCodeBit

    fun alarmIntent(
        context: Context,
        alarmId: AlarmId,
        kind: AlarmOccurrenceKind,
        scheduledFor: Instant,
        parentOccurrenceId: OccurrenceId?,
        snoozesRemaining: Int,
    ): Intent = Intent(context, AlarmReceiver::class.java).apply {
        action = ACTION_ALARM
        putExtra(EXTRA_ALARM_ID, alarmId.value)
        putExtra(EXTRA_SCHEDULED_FOR, scheduledFor.toEpochMilli())
        putExtra(EXTRA_KIND, kind.name)
        putExtra(EXTRA_PARENT_OCCURRENCE_ID, parentOccurrenceId?.value ?: -1L)
        putExtra(EXTRA_SNOOZES_REMAINING, snoozesRemaining)
    }
}
