package dev.upyet.timer.playback

import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.time.TimerClock
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where a ringing timer's run state is read and written: Room once the user is unlocked, the device-protected
 * mirror before (AGENTS.md §11). Bound to `TimerDiscovery`, whose lock also serialises expiry and boot.
 */
interface TimerRingingStore {
    /** The timer when it is stored as Ringing; null when it is gone or no longer Ringing. Throws on a storage failure. */
    suspend fun ringing(id: TimerId): Timer?

    /** ADR 0008 `stop`: Ringing → Idle at its full length. A timer that is not Ringing is left as it is. */
    suspend fun stop(id: TimerId)

    /** ADR 0008 `+1 min`: Ringing → Running at 60 s, re-armed. A timer that is not Ringing is left as it is. */
    suspend fun addMinute(id: TimerId)
}

/** The one sound and vibration every ringing timer shares (ADR 0008). */
interface TimerRingingOutput {
    fun start(ringtoneUri: String?)

    fun stop()
}

/**
 * The timer ringing state machine below `TimerPlaybackService`: who is ringing, when the one sound starts and
 * stops, and what Stop, +1 min and Stop all persist. Plain Kotlin with no Android, Compose, Room or camera
 * dependency (AGENTS.md §8a), so it is unit-tested against a fake player and a fake store.
 *
 * Silencing never waits on storage: a timer leaves [registry] and, when it was the last, the sound stops before
 * the store is written, and a failed write is logged rather than keeping the timer ringing (invariant 3). +1 min
 * takes a timer out of the showing exactly like Stop (ADR 0010); only what is persisted differs.
 */
class TimerRinging(
    private val registry: TimerRingingRegistry,
    private val store: TimerRingingStore,
    private val output: TimerRingingOutput,
    private val clock: TimerClock,
) {
    // The service already runs commands one at a time; the lock keeps that true for a second caller (slice #38).
    private val lock = Mutex()

    /** A validated expiry for [id]. Joins the current ringing, or starts it and the sound. Returns who rings now. */
    suspend fun ring(id: TimerId): List<RingingTimer> = lock.withLock {
        if (registry.ringing.value.any { it.id == id }) return@withLock registry.ringing.value
        val joining = try {
            // Bounded: the sound must not wait on Room. A read that does not answer in time rings with defaults.
            val read = withTimeoutOrNull(READ_TIMEOUT_MILLIS) { Read(store.ringing(id)) }
            when {
                read == null -> unreadable(id, "ring_read_timed_out")

                read.timer == null -> {
                    AlarmLog.event("timer_ring_stale", "timerId" to id.value)
                    return@withLock registry.ringing.value
                }

                else -> read.timer.let {
                    RingingTimer(it.id, it.label, it.length, it.ringtoneUri, it.evidenceEnabled, clock.now().wallClock)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // A timer whose expiry was validated must ring even if its details cannot be read back.
            AlarmLog.event("timer_error", "reason" to "ring_read_failed", "error" to error.javaClass.simpleName)
            unreadable(id, reason = null)
        }
        val wasSilent = registry.ringing.value.isEmpty()
        registry.add(joining)
        if (wasSilent) output.start(joining.ringtoneUri)
        val now = registry.ringing.value
        AlarmLog.event("timer_ringing_started", "timerId" to id.value, "ringing" to now.size)
        now
    }

    suspend fun stop(id: TimerId): List<RingingTimer> = lock.withLock {
        leave(listOf(id))
        persist("stop", id) { store.stop(it) }
        AlarmLog.event("timer_stopped", "timerId" to id.value, "ringing" to registry.ringing.value.size)
        registry.ringing.value
    }

    suspend fun addMinute(id: TimerId): List<RingingTimer> = lock.withLock {
        leave(listOf(id))
        persist("addMinute", id) { store.addMinute(it) }
        AlarmLog.event("timer_minute_added", "timerId" to id.value, "ringing" to registry.ringing.value.size)
        registry.ringing.value
    }

    suspend fun stopAll(): List<RingingTimer> = lock.withLock {
        val ids = registry.ringing.value.map { it.id }
        leave(ids)
        ids.forEach { id -> persist("stop", id) { store.stop(it) } }
        AlarmLog.event("timer_stopped_all", "stopped" to ids.size)
        registry.ringing.value
    }

    private class Read(val timer: Timer?)

    private fun unreadable(id: TimerId, reason: String?): RingingTimer {
        reason?.let { AlarmLog.event("timer_error", "reason" to it, "timerId" to id.value) }
        return RingingTimer(
            id,
            label = "",
            length = null,
            ringtoneUri = null,
            evidenceEnabled = false,
            ringingSince = clock.now().wallClock,
            detailsUnreadable = true,
        )
    }

    /** Takes [ids] out of the showing; the sound stops with the last one. Never touches the store. */
    private fun leave(ids: List<TimerId>) {
        val wasRinging = registry.ringing.value.isNotEmpty()
        ids.forEach(registry::remove)
        if (wasRinging && registry.ringing.value.isEmpty()) output.stop()
    }

    private suspend fun persist(what: String, id: TimerId, write: suspend (TimerId) -> Unit) {
        try {
            // The user already heard the ring end: a service torn down mid-write must still record that.
            withContext(NonCancellable) { write(id) }
        } catch (error: Exception) {
            // Silence is not undone. The timer stays stored as Ringing, so the Timer tab shows it ringing until
            // the user stops it there; ringing it again after a failed write would be worse than that.
            AlarmLog.event("timer_error", "reason" to "${what}_not_persisted", "timerId" to id.value, "error" to error.javaClass.simpleName)
        }
    }

    private companion object {
        const val READ_TIMEOUT_MILLIS = 1_000L
    }
}
