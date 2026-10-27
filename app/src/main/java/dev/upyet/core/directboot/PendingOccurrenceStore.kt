package dev.upyet.core.directboot

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.evidence.domain.EvidenceErrorCode
import dev.upyet.evidence.domain.OccurrenceSource
import dev.upyet.evidence.domain.TimerAttribution
import dev.upyet.timer.domain.TimerId
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An occurrence that rang before first unlock, waiting in device-protected storage for `USER_UNLOCKED` to copy it into
 * Room. [source] is the discriminator reconciliation dispatches on (ADR 0010): a timer record is never applied as an
 * alarm record.
 */
sealed interface PendingRecord {
    val source: OccurrenceSource
    val triggeredAt: Instant
    val dismissedAt: Instant?
    val outcome: String
}

data class PendingOccurrence(
    val alarmId: AlarmId,
    val scheduledFor: Instant,
    override val triggeredAt: Instant,
    override val dismissedAt: Instant?,
    override val outcome: String,
) : PendingRecord {
    override val source: OccurrenceSource get() = OccurrenceSource.ALARM
}

/**
 * One showing of the timer ringing screen that opened before first unlock. [timers] is the recording-on attribution
 * snapshot; [evidenceError] is why nothing was recorded, always `DIRECT_BOOT_UNAVAILABLE` today (AGENTS.md §11).
 */
data class PendingTimerOccurrence(
    val timers: List<TimerAttribution>,
    override val triggeredAt: Instant,
    override val dismissedAt: Instant?,
    override val outcome: String,
    val evidenceError: EvidenceErrorCode = EvidenceErrorCode.DIRECT_BOOT_UNAVAILABLE,
) : PendingRecord {
    override val source: OccurrenceSource get() = OccurrenceSource.TIMER
}

/** Direct-Boot-safe: device-protected SharedPreferences only, never Room or DataStore (AGENTS.md §11). */
interface PendingOccurrences {
    /**
     * Upserts by [PendingOccurrenceCodec.identity]: the same occurrence is recorded once when it starts ringing and again
     * when it ends, and reconciliation after unlock must produce exactly one row.
     */
    fun append(record: PendingRecord)

    /**
     * Upserts [record] only while a record with its identity is still waiting. Once reconciliation has copied the
     * occurrence into Room and cleared the store, a late write would otherwise become a second row at the next unlock.
     */
    fun replaceIfPending(record: PendingRecord): Boolean

    fun all(): List<PendingRecord>

    fun clear()
}

/**
 * The record encoding. Version 2 writes `2|<source>|...`:
 * - `2|ALARM|alarmId|scheduledForMs|triggeredAtMs|dismissedAtMs(-1)|outcome`
 * - `2|TIMER|triggeredAtMs|dismissedAtMs(-1)|outcome|evidenceError|id:label,id:label` with each label URL-encoded, so
 *   no label can forge a separator.
 *
 * A version 1 record, `alarmId|scheduledForMs|triggeredAtMs|dismissedAtMs(-1)|outcome`, was written before the
 * discriminator existed and decodes as an alarm record, so an upgrade never drops an in-flight alarm. Its first field
 * is a number, so it can never be mistaken for a version tag.
 */
object PendingOccurrenceCodec {
    private const val SEPARATOR = "|"
    private const val VERSION = "2"
    private const val LEGACY_FIELDS = 5
    private const val ALARM_FIELDS = 7
    private const val TIMER_FIELDS = 7
    private const val NO_TIME = -1L
    private const val CHARSET = "UTF-8"

    /** What [PendingOccurrences.append] upserts by: the alarm and its scheduled time, or the showing's first ring. */
    fun identity(record: PendingRecord): String = when (record) {
        is PendingOccurrence -> "${OccurrenceSource.ALARM}:${record.alarmId.value}:${record.scheduledFor.toEpochMilli()}"
        is PendingTimerOccurrence -> "${OccurrenceSource.TIMER}:${record.triggeredAt.toEpochMilli()}"
    }

    fun encode(record: PendingRecord): String = when (record) {
        is PendingOccurrence -> listOf(
            VERSION,
            OccurrenceSource.ALARM.name,
            record.alarmId.value,
            record.scheduledFor.toEpochMilli(),
            record.triggeredAt.toEpochMilli(),
            record.dismissedAt.orNoTime(),
            record.outcome,
        )

        is PendingTimerOccurrence -> listOf(
            VERSION,
            OccurrenceSource.TIMER.name,
            record.triggeredAt.toEpochMilli(),
            record.dismissedAt.orNoTime(),
            record.outcome,
            record.evidenceError.name,
            record.timers.joinToString(",") { "${it.timerId.value}:${URLEncoder.encode(it.label, CHARSET)}" },
        )
    }.joinToString(SEPARATOR)

    /** Null for anything malformed or from an unknown version, so one bad entry cannot hide the others. */
    fun decode(value: String): PendingRecord? {
        val parts = value.split(SEPARATOR)
        return runCatching {
            when {
                parts.size == LEGACY_FIELDS -> alarm(parts)
                parts.firstOrNull() != VERSION -> null
                parts[1] == OccurrenceSource.ALARM.name && parts.size == ALARM_FIELDS -> alarm(parts.drop(2))
                parts[1] == OccurrenceSource.TIMER.name && parts.size == TIMER_FIELDS -> timer(parts.drop(2))
                else -> null
            }
        }.getOrNull()
    }

    private fun alarm(p: List<String>) = PendingOccurrence(
        alarmId = AlarmId(p[0].toLong()),
        scheduledFor = Instant.ofEpochMilli(p[1].toLong()),
        triggeredAt = Instant.ofEpochMilli(p[2].toLong()),
        dismissedAt = p[3].toTime(),
        outcome = p[4],
    )

    private fun timer(p: List<String>) = PendingTimerOccurrence(
        timers = p[4].takeIf { it.isNotEmpty() }?.split(",").orEmpty().map { entry ->
            val (id, label) = entry.split(":", limit = 2).also { require(it.size == 2) }
            TimerAttribution(TimerId(id.toLong()), URLDecoder.decode(label, CHARSET))
        },
        triggeredAt = Instant.ofEpochMilli(p[0].toLong()),
        dismissedAt = p[1].toTime(),
        outcome = p[2],
        evidenceError = EvidenceErrorCode.valueOf(p[3]),
    )

    private fun Instant?.orNoTime(): Long = this?.toEpochMilli() ?: NO_TIME

    private fun String.toTime(): Instant? = toLong().takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
}

@Singleton
class PendingOccurrenceStore @Inject constructor(@ApplicationContext context: Context) : PendingOccurrences {
    private val preferences = context.createDeviceProtectedStorageContext().getSharedPreferences(
        "pending_occurrences",
        Context.MODE_PRIVATE,
    )

    @Synchronized
    override fun append(record: PendingRecord) {
        val identity = PendingOccurrenceCodec.identity(record)
        val values = all().filterNot { PendingOccurrenceCodec.identity(it) == identity } + record
        val kept = values.sortedBy { it.triggeredAt }.takeLast(MAX_RECORDS)
        preferences.edit { putStringSet(KEY, kept.map(PendingOccurrenceCodec::encode).toSet()) }
    }

    @Synchronized
    override fun replaceIfPending(record: PendingRecord): Boolean {
        val identity = PendingOccurrenceCodec.identity(record)
        if (all().none { PendingOccurrenceCodec.identity(it) == identity }) return false
        append(record)
        return true
    }

    override fun all(): List<PendingRecord> = preferences.getStringSet(KEY, emptySet()).orEmpty().mapNotNull(PendingOccurrenceCodec::decode)

    @Synchronized
    override fun clear() {
        preferences.edit { remove(KEY) }
    }

    private companion object {
        const val KEY = "records"
        const val MAX_RECORDS = 50
    }
}
