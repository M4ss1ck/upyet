package dev.upyet.timer.scheduling

import dagger.Lazy
import dev.upyet.core.directboot.TimerMirror
import dev.upyet.core.directboot.TimerMirrorReconciler
import dev.upyet.core.directboot.TimerReconciliation
import dev.upyet.core.directboot.toMirrored
import dev.upyet.core.directboot.toTimer
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.time.TimerClock
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRepository
import dev.upyet.timer.domain.TimerRunState
import dev.upyet.timer.domain.TimerTransition
import dev.upyet.timer.playback.TimerRingingStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class TimerExpiry {
    /** The delivery matched the timer's current end: it is stored as Ringing and handed to playback. */
    RANG,

    /** The timer is gone, not Running, or ends at another instant: nothing rings (AGENTS.md §6a). */
    STALE,
}

/**
 * Every timer event that does not come from the user: boot, unlock, launch, expiry, a wall-clock or zone change and a
 * re-arm (AGENTS.md §6a, ADR 0008, ADR 0009). Each one runs against the store that is authoritative right now:
 * Room once the user is unlocked, after reconciling the mirror into it as [TimerMirrorReconciler] requires, and
 * the device-protected mirror before. Room sits behind [Lazy] and is built only on the unlocked path (§11).
 *
 * It is also the [TimerRingingStore] `TimerPlaybackService` writes Stop and +1 min through, before and after
 * first unlock alike, so those two never touch Room from a Direct Boot path either.
 *
 * One lock serialises these events, so an expiry cannot interleave with a boot pass, a Stop or a +1 min over
 * the same timer. User edits go through [TimerEditor], which takes the same lock: the Timer tab and the
 * running-timer notification both route their read-modify-write through here, so an edit cannot land between
 * an expiry's read and its write and be overwritten.
 */
@Singleton
class TimerDiscovery @Inject constructor(
    private val mirror: TimerMirror,
    private val repository: Lazy<TimerRepository>,
    private val reconciler: TimerMirrorReconciler,
    private val clock: TimerClock,
    private val alarms: TimerAlarmSync,
    private val handoff: TimerRingingHandoff,
) : TimerRearm,
    TimerRingingStore,
    TimerEditor {
    private val lock = Mutex()

    /** `LOCKED_BOOT_COMPLETED` and `BOOT_COMPLETED`: elapsed realtime restarted, so measure on the wall clock. */
    suspend fun onBoot(): TimerSyncResult = lock.withLock { discover(open().store, afterBoot = true, trigger = "boot") }

    /** `USER_UNLOCKED`: the mirror reaches Room first, then discovery runs on Room. */
    suspend fun onUserUnlocked(): TimerReconciliation = lock.withLock {
        val opened = open()
        discover(opened.store, afterBoot = false, trigger = "unlock")
        opened.reconciliation
    }

    /** App launch, a package update and restored exact-alarm access: discovery, then re-arm. Idempotent. */
    override suspend fun rearm() {
        lock.withLock { discover(open().store, afterBoot = false, trigger = "rearm") }
    }

    /**
     * `TIME_SET`: re-anchor running timers from the elapsed delta. Their end on the elapsed clock is unchanged,
     * so re-arming programs the same trigger and only refreshes the end instant the delivery is checked against.
     * Before first unlock nothing is touched: the mirror has no elapsed anchor to measure the change with, and
     * the armed alarm still fires on time and still matches the mirror's end.
     */
    suspend fun onWallClockChanged() {
        lock.withLock {
            val opened = open()
            if (opened.reconciliation == TimerReconciliation.Locked) {
                AlarmLog.event("timer_wall_clock_change_skipped", "reason" to "locked")
                return@withLock
            }
            val now = clock.now()
            var changed = 0
            opened.store.all().forEach { timer ->
                (timer.onWallClockChanged(now) as? TimerTransition.Applied)?.let {
                    opened.store.save(it.timer)
                    changed++
                }
            }
            val result = alarms.sync { opened.store.all() }
            AlarmLog.event("timer_wall_clock_changed", "reanchored" to changed, "armedFor" to result.alarm?.timerId?.value)
        }
    }

    /**
     * `TIMEZONE_CHANGED`: both anchors are zone-free, so no timer changes and nothing re-arms (AGENTS.md §6a). Only the
     * running notice reads the zone, so it is re-posted. Before first unlock it is left as posted, like [onWallClockChanged].
     */
    suspend fun onZoneChanged() {
        lock.withLock {
            val opened = open()
            if (opened.reconciliation == TimerReconciliation.Locked) {
                AlarmLog.event("timer_zone_change_skipped", "reason" to "locked")
                return@withLock
            }
            alarms.refreshRunningNotice { opened.store.all() }
            AlarmLog.event("timer_zone_changed")
        }
    }

    /** The exact alarm fired for [timerId], armed for [endsAt]. Persists before handing off, so playback sees Ringing. */
    suspend fun onExpiry(timerId: TimerId, endsAt: Instant): TimerExpiry = lock.withLock {
        val store = open().store
        val timer = store.get(timerId)
        val transition = timer?.onExpiry(endsAt)
        if (transition is TimerTransition.Applied) {
            store.save(transition.timer)
            alarms.sync { store.all() }
            AlarmLog.event("timer_expired", "timerId" to timerId.value)
            handoff.ring(timerId)
            TimerExpiry.RANG
        } else {
            AlarmLog.event("timer_delivery_stale", "timerId" to timerId.value, "reason" to staleReason(timer))
            // The alarm that should be armed may not be this one any more: re-arming is idempotent and cheap.
            alarms.sync { store.all() }
            TimerExpiry.STALE
        }
    }

    override suspend fun ringing(id: TimerId): Timer? = lock.withLock {
        open().store.get(id)?.takeIf { it.runState == TimerRunState.Ringing }
    }

    override suspend fun stop(id: TimerId) {
        lock.withLock { applyToRinging(id, "stop") { it.stop() } }
    }

    override suspend fun addMinute(id: TimerId) {
        lock.withLock { applyToRinging(id, "add_minute") { it.addMinute(clock.now()) } }
    }

    /** Only a Ringing timer changes: a late Stop for a timer the user already restarted must not reset it. */
    private suspend fun applyToRinging(id: TimerId, command: String, transition: (Timer) -> TimerTransition) {
        val store = open().store
        val timer = store.get(id)?.takeIf { it.runState == TimerRunState.Ringing }
        val applied = timer?.let(transition) as? TimerTransition.Applied
        if (applied == null) {
            AlarmLog.event("timer_command_ignored", "command" to command, "timerId" to id.value, "reason" to "not_ringing")
            return
        }
        store.save(applied.timer)
        alarms.sync { store.all() }
    }

    /**
     * A user creates a timer. Only ever called from the UI, which runs after first unlock: Room is the store, and
     * [RoomTimerRepository] mirrors and re-arms the write itself, so the sync here only covers the list as a whole.
     */
    override suspend fun add(timer: Timer): TimerId = lock.withLock {
        val opened = open()
        check(opened.reconciliation != TimerReconciliation.Locked) { "timer add before unlock" }
        val id = repository.get().upsert(timer)
        alarms.sync { opened.store.all() }
        id
    }

    /**
     * A user edit (Timer tab, running-timer notification). A Ringing timer is refused here: its Stop and +1 min
     * belong to [TimerRingingStore], and a stale tap must not change a ring the user is already answering.
     */
    override suspend fun edit(
        id: TimerId,
        command: String,
        onlyIf: (Timer) -> Boolean,
        transition: (Timer, TimerAnchor) -> TimerTransition,
    ): TimerTransition = lock.withLock {
        val store = open().store
        val timer = store.get(id) ?: return@withLock refuse(command, id, "no_timer")
        if (timer.runState == TimerRunState.Ringing) return@withLock refuse(command, id, "ringing")
        if (!onlyIf(timer)) return@withLock refuse(command, id, "precondition")
        when (val result = transition(timer, clock.now())) {
            is TimerTransition.Applied -> {
                store.save(result.timer)
                alarms.sync { store.all() }
                AlarmLog.event("timer_edited", "command" to command, "timerId" to id.value)
                result
            }

            TimerTransition.Removed -> {
                // The mirror has no delete: a timer removed before first unlock would come back on the next boot.
                if (store is MirrorStore) return@withLock refuse(command, id, "locked")
                store.delete(id)
                alarms.sync { store.all() }
                AlarmLog.event("timer_edited", "command" to command, "timerId" to id.value)
                result
            }

            TimerTransition.NoOp, TimerTransition.NotOffered -> {
                AlarmLog.event(
                    "timer_command_ignored",
                    "command" to command,
                    "timerId" to id.value,
                    "reason" to result.javaClass.simpleName.lowercase(),
                )
                result
            }
        }
    }

    private fun refuse(command: String, id: TimerId, reason: String): TimerTransition {
        AlarmLog.event("timer_command_ignored", "command" to command, "timerId" to id.value, "reason" to reason)
        return TimerTransition.NoOp
    }

    private suspend fun discover(store: TimerStore, afterBoot: Boolean, trigger: String): TimerSyncResult {
        val now = clock.now()
        var missed = 0
        store.all().forEach { timer ->
            val updated = (timer.onDiscovery(now, afterBoot) as? TimerTransition.Applied)?.timer ?: return@forEach
            // Within one boot the elapsed anchor is still good, so only a change of state is worth a write:
            // re-anchoring every running timer on each launch would rewrite it and nudge its alarm by the
            // jitter between the two clock reads. After a boot the re-anchor is the point.
            if (updated != timer && (afterBoot || updated.runState::class != timer.runState::class)) {
                store.save(updated)
                if (updated.runState == TimerRunState.Missed) missed++
            }
        }
        val result = alarms.sync(newlyMissed = missed > 0) { store.all() }
        AlarmLog.event(
            "timer_discovery",
            "trigger" to trigger,
            "store" to store.name,
            "missed" to missed,
            "armedFor" to result.alarm?.timerId?.value,
            "result" to result.arm?.javaClass?.simpleName,
        )
        return result
    }

    private fun staleReason(timer: Timer?): String = when {
        timer == null -> "no_timer"
        timer.runState !is TimerRunState.Running -> "not_running"
        else -> "end_changed"
    }

    private class Opened(val store: TimerStore, val reconciliation: TimerReconciliation)

    /** Reconciling is also the unlock check: [TimerReconciliation.Locked] means Room must not be touched. */
    private suspend fun open(): Opened {
        val reconciliation = reconciler.reconcile()
        val store = if (reconciliation == TimerReconciliation.Locked) MirrorStore() else RoomStore(repository.get())
        return Opened(store, reconciliation)
    }

    private interface TimerStore {
        val name: String

        suspend fun all(): List<Timer>

        suspend fun get(id: TimerId): Timer? = all().find { it.id == id }

        suspend fun save(timer: Timer)

        suspend fun delete(id: TimerId)
    }

    /** Room's writes mirror themselves and re-arm (RoomTimerRepository); the final sync here covers no-change passes. */
    private class RoomStore(private val timers: TimerRepository) : TimerStore {
        override val name = "room"

        override suspend fun all(): List<Timer> = timers.observeTimers().first()

        override suspend fun get(id: TimerId): Timer? = timers.getTimer(id)

        override suspend fun save(timer: Timer) {
            timers.upsert(timer)
        }

        override suspend fun delete(id: TimerId) {
            timers.delete(id)
        }
    }

    /** Before first unlock. `createdAt` is not mirrored and nothing on this path reads it, so it is the epoch. */
    private inner class MirrorStore : TimerStore {
        override val name = "mirror"

        override suspend fun all(): List<Timer> {
            val now = clock.now()
            return mirror.all().mapNotNull { it.toTimer(now, Instant.EPOCH) }
        }

        override suspend fun save(timer: Timer) {
            mirror.put(timer.toMirrored())
        }

        /** Refused by [edit] before it gets here: the mirror has no delete, and nothing on this path may build Room. */
        override suspend fun delete(id: TimerId): Unit = error("timer $id cannot be deleted before first unlock")
    }
}
