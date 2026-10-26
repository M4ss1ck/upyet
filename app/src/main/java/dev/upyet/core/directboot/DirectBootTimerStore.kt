package dev.upyet.core.directboot

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.timer.domain.TimerId
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The run state as the mirror encodes it; kept independent of Room's encoding, like [MirroredRecurrence]. */
enum class MirroredTimerState { IDLE, RUNNING, PAUSED, RINGING, MISSED }

/**
 * The minimal device-protected copy of one timer (ADR 0008, AGENTS.md §11): what a timer needs to ring before
 * first unlock, and nothing else. The label is here on purpose: before first unlock it is the only way the
 * ringing screen can tell one ringing timer from another. The evidence flag is here because nothing else can
 * tell the pre-unlock path which timers are recording-on (ADR 0010).
 *
 * There is no elapsed-realtime anchor: it restarts at boot, and the mirror exists for the boot that follows.
 */
data class MirroredTimer(
    val timerId: TimerId,
    val state: MirroredTimerState,
    /** The chosen length, which Reset restores. */
    val length: Duration,
    /** Running: remaining at [wallClockAnchor]. Paused: the frozen remaining time. Otherwise null. */
    val remainingAtAnchor: Duration?,
    /** Running only. */
    val wallClockAnchor: Instant?,
    /** Running only: [wallClockAnchor] plus [remainingAtAnchor]. */
    val endsAt: Instant?,
    val label: String,
    val ringtoneUri: String?,
    val evidenceEnabled: Boolean,
    /**
     * Written before first unlock, so it may hold a run-state change Room has not seen. Only such a record is
     * reconciled into Room on unlock; any other record is at most as new as Room and must never overwrite it.
     * The store sets it, never the caller, and once set only [TimerMirror.markReconciled] clears it: a write
     * after unlock that lands before reconciliation must not hide a run state Room has not seen yet.
     */
    val writtenWhileLocked: Boolean = false,
)

interface TimerMirror {
    /**
     * Stores [record], marked [MirroredTimer.writtenWhileLocked] when the user is still locked or when the record
     * it replaces was still waiting for reconciliation.
     */
    fun put(record: MirroredTimer)

    /** Clears [MirroredTimer.writtenWhileLocked] once Room holds that run state. Only reconciliation calls it. */
    fun markReconciled(timerId: TimerId)

    fun remove(timerId: TimerId)

    fun all(): List<MirroredTimer>

    fun clear()
}

/**
 * Encodes one record as `timer:<id>` → `state|lengthMs|remainingMs|wallAnchorMs|endsAtMs|label|ringtoneUri|evidence|writtenWhileLocked`,
 * absent values empty. Fields are only ever appended: [decode] accepts a record that predates a trailing
 * field and ignores fields a later build appended. Label and URI are URL-encoded, so a `|` in either cannot
 * shift the fields after it.
 */
object MirroredTimerCodec {
    private const val KEY_PREFIX = "timer:"
    private const val SEPARATOR = "|"
    private const val CHARSET = "UTF-8"

    /** Everything up to and including the evidence flag; later fields are optional. */
    private const val REQUIRED_FIELDS = 8

    fun key(timerId: TimerId): String = "$KEY_PREFIX${timerId.value}"

    fun encode(record: MirroredTimer): String = listOf(
        record.state.name,
        record.length.toMillis(),
        record.remainingAtAnchor?.toMillis() ?: "",
        record.wallClockAnchor?.toEpochMilli() ?: "",
        record.endsAt?.toEpochMilli() ?: "",
        URLEncoder.encode(record.label, CHARSET),
        URLEncoder.encode(record.ringtoneUri.orEmpty(), CHARSET),
        record.evidenceEnabled,
        record.writtenWhileLocked,
    ).joinToString(SEPARATOR)

    /** Null for anything that is not a well-formed timer record, so one bad entry cannot hide the others. */
    fun decode(key: String, value: String): MirroredTimer? {
        if (!key.startsWith(KEY_PREFIX)) return null
        val id = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: return null
        val parts = value.split(SEPARATOR)
        if (parts.size < REQUIRED_FIELDS) return null
        return runCatching {
            MirroredTimer(
                timerId = TimerId(id),
                state = MirroredTimerState.valueOf(parts[0]),
                length = Duration.ofMillis(parts[1].toLong()),
                remainingAtAnchor = parts[2].optionalLong()?.let(Duration::ofMillis),
                wallClockAnchor = parts[3].optionalLong()?.let(Instant::ofEpochMilli),
                endsAt = parts[4].optionalLong()?.let(Instant::ofEpochMilli),
                label = URLDecoder.decode(parts[5], CHARSET),
                ringtoneUri = URLDecoder.decode(parts[6], CHARSET).ifEmpty { null },
                evidenceEnabled = parts[7].toBooleanStrict(),
                writtenWhileLocked = parts.getOrNull(8)?.toBooleanStrictOrNull() ?: false,
            )
        }.getOrNull()
    }

    /** Empty means absent; anything else must be a number, or the record is malformed. */
    private fun String.optionalLong(): Long? = if (isEmpty()) null else toLong()
}

/** The timer mirror in device-protected SharedPreferences, readable before first unlock (AGENTS.md §11). */
@Singleton
class DirectBootTimerStore @Inject constructor(@ApplicationContext context: Context, private val userUnlockState: UserUnlockState) :
    TimerMirror {
    private val preferences: SharedPreferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun put(record: MirroredTimer) {
        val pending = !userUnlockState.isUserUnlocked() || read(record.timerId)?.writtenWhileLocked == true
        write(record.copy(writtenWhileLocked = pending))
    }

    override fun markReconciled(timerId: TimerId) {
        read(timerId)?.let { write(it.copy(writtenWhileLocked = false)) }
    }

    override fun remove(timerId: TimerId) {
        preferences.edit { remove(MirroredTimerCodec.key(timerId)) }
    }

    override fun all(): List<MirroredTimer> = preferences.all.mapNotNull { (key, value) ->
        (value as? String)?.let { MirroredTimerCodec.decode(key, it) }
    }

    override fun clear() {
        preferences.edit { clear() }
    }

    private fun read(timerId: TimerId): MirroredTimer? {
        val key = MirroredTimerCodec.key(timerId)
        return preferences.getString(key, null)?.let { MirroredTimerCodec.decode(key, it) }
    }

    private fun write(record: MirroredTimer) {
        preferences.edit { putString(MirroredTimerCodec.key(record.timerId), MirroredTimerCodec.encode(record)) }
    }

    private companion object {
        const val PREFERENCES_NAME = "timer_mirror"
    }
}
