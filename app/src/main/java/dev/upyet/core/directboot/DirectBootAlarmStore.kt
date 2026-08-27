package dev.upyet.core.directboot

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.SnoozeBudget
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class MirroredAlarm(
    val alarmId: AlarmId,
    val kind: AlarmOccurrenceKind,
    val triggerAt: Instant,
    val snoozeMinutes: Int,
    val vibrationEnabled: Boolean,
    val soundUri: String?,
    val minuteOfDay: Int,
    val recurrenceType: String,
    val weekdayMask: Int,
    val snoozesRemaining: Int,
    val chainStartedAtMillis: Long,
    val skipNextOnEpochDay: Long? = null,
    val soundEnabled: Boolean = true,
)

interface AlarmMirror {
    fun put(record: MirroredAlarm)
    fun remove(alarmId: AlarmId, kind: AlarmOccurrenceKind)
    fun all(): List<MirroredAlarm>
    fun nextTrigger(): Instant?
    fun clear()
}

@Singleton
class DirectBootAlarmStore @Inject constructor(@ApplicationContext context: Context) : AlarmMirror {
    private val preferences: SharedPreferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("alarm_mirror", Context.MODE_PRIVATE)

    override fun put(record: MirroredAlarm) {
        preferences.edit {
            putString(
                key(record.alarmId, record.kind),
                listOf(
                    record.triggerAt.toEpochMilli(),
                    record.snoozeMinutes,
                    record.vibrationEnabled,
                    record.soundUri ?: "",
                    record.minuteOfDay,
                    record.recurrenceType,
                    record.weekdayMask,
                    record.snoozesRemaining,
                    record.chainStartedAtMillis,
                    record.skipNextOnEpochDay?.toString() ?: "",
                    record.soundEnabled,
                ).joinToString("|"),
            )
        }
    }

    override fun remove(alarmId: AlarmId, kind: AlarmOccurrenceKind) {
        preferences.edit { remove(key(alarmId, kind)) }
    }

    override fun all(): List<MirroredAlarm> = preferences.all.mapNotNull { (key, value) ->
        if (value !is String) return@mapNotNull null
        val parts = value.split('|')
        if (parts.size !in 4..11) return@mapNotNull null
        runCatching {
            val identity = key.split(':')
            MirroredAlarm(
                AlarmId(identity[1].toLong()),
                AlarmOccurrenceKind.valueOf(identity[2]),
                Instant.ofEpochMilli(parts[0].toLong()),
                parts[1].toInt(),
                parts[2].toBoolean(),
                parts[3].ifEmpty {
                    null
                },
                parts.getOrNull(4)?.toInt() ?: 0,
                parts.getOrNull(5) ?: "ONE_TIME",
                parts.getOrNull(6)?.toInt() ?: 0,
                parts.getOrNull(7)?.toIntOrNull() ?: SnoozeBudget.UNSET,
                parts.getOrNull(8)?.toLongOrNull() ?: 0L,
                parts.getOrNull(9)?.toLongOrNull(),
                parts.getOrNull(10)?.toBooleanStrictOrNull() ?: true,
            )
        }.getOrNull()
    }

    override fun nextTrigger(): Instant? = all().minOfOrNull { it.triggerAt }
    override fun clear() {
        preferences.edit { clear() }
    }
    private fun key(alarmId: AlarmId, kind: AlarmOccurrenceKind) = "alarm:${alarmId.value}:${kind.name}"
}
