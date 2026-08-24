package dev.myalarm.core.directboot

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.myalarm.alarm.domain.AlarmId
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class PendingOccurrence(
    val alarmId: AlarmId,
    val scheduledFor: Instant,
    val triggeredAt: Instant,
    val dismissedAt: Instant?,
    val outcome: String,
)

/** Records use alarmId|scheduledForMillis|triggeredAtMillis|dismissedAtMillis(-1)|outcome. */
@Singleton
class PendingOccurrenceStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.createDeviceProtectedStorageContext().getSharedPreferences(
        "pending_occurrences",
        Context.MODE_PRIVATE,
    )

    /**
     * Upserts by (alarmId, scheduledFor): the same occurrence is recorded once when it starts ringing and
     * again when it is dismissed or snoozed, and reconciliation after unlock must produce exactly one row.
     */
    fun append(record: PendingOccurrence) {
        val values = all().filterNot { it.alarmId == record.alarmId && it.scheduledFor == record.scheduledFor } + record
        val kept = values.sortedBy { it.triggeredAt }.takeLast(MAX_RECORDS)
        preferences.edit { putStringSet(KEY, kept.map(::encode).toSet()) }
    }
    fun all(): List<PendingOccurrence> = preferences.getStringSet(KEY, emptySet()).orEmpty().mapNotNull(::decode)
    fun clear() {
        preferences.edit { remove(KEY) }
    }
    private fun encode(r: PendingOccurrence) = listOf(
        r.alarmId.value,
        r.scheduledFor.toEpochMilli(),
        r.triggeredAt.toEpochMilli(),
        r.dismissedAt?.toEpochMilli() ?: -1,
        r.outcome,
    ).joinToString("|")
    private fun decode(value: String) = value.split('|').takeIf { it.size == 5 }?.let { p ->
        runCatching {
            PendingOccurrence(
                AlarmId(p[0].toLong()),
                Instant.ofEpochMilli(p[1].toLong()),
                Instant.ofEpochMilli(p[2].toLong()),
                p[3].toLong().takeIf {
                    it >=
                        0
                }?.let(Instant::ofEpochMilli),
                p[4],
            )
        }.getOrNull()
    }
    private companion object {
        const val KEY = "records"
        const val MAX_RECORDS = 50
    }
}
