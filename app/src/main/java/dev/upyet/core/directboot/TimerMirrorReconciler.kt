package dev.upyet.core.directboot

import dagger.Lazy
import dev.upyet.core.time.TimerClock
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed interface TimerReconciliation {
    /** The user is still locked: neither Room nor the mirror was changed. */
    data object Locked : TimerReconciliation

    /** [applied] run states copied into Room; [dropped] mirror records whose timer Room no longer has. */
    data class Done(val applied: Int, val dropped: Int) : TimerReconciliation
}

/**
 * `USER_UNLOCKED`: brings Room up to date with what happened to timers before first unlock (ADR 0008).
 *
 * Room is the truth for the list, labels, lengths and Timer evidence, none of which can change while locked,
 * so only the run state crosses over, and only from a record written while locked. Any other record is at most
 * as new as Room, which is what keeps this idempotent: once applied, the record is marked reconciled, so a
 * second pass, or one after the user edited the timer, changes nothing. A record for a timer Room does not have
 * is dropped, never turned into a timer.
 *
 * Contract: any path that writes timers after unlock calls [reconcile] first; `TimerDiscovery` does, for
 * boot, unlock, launch, expiry and wall-clock changes, and the Timer tab must too. It is idempotent and cheap,
 * and it is the only way a run state that changed before unlock survives a writer that would otherwise mirror
 * Room's older copy over it.
 *
 * Room is behind [Lazy] and built only after [UserUnlockState.isUserUnlocked] holds (AGENTS.md §11).
 */
class TimerMirrorReconciler @Inject constructor(
    private val mirror: TimerMirror,
    private val repository: Lazy<TimerRepository>,
    private val userUnlockState: UserUnlockState,
    private val clock: TimerClock,
) {
    suspend fun reconcile(): TimerReconciliation {
        if (!userUnlockState.isUserUnlocked()) return TimerReconciliation.Locked
        val timers = repository.get()
        val stored = timers.observeTimers().first().associateBy { it.id }
        val now = clock.now()
        var applied = 0
        var dropped = 0
        mirror.all().forEach { record ->
            val inRoom = stored[record.timerId]
            when {
                inRoom == null -> {
                    mirror.remove(record.timerId)
                    dropped++
                }

                !record.writtenWhileLocked -> Unit

                else -> {
                    val fromMirror = record.toTimer(now, inRoom.createdAt)
                    if (fromMirror == null) {
                        // Unreadable as a timer: Room's copy is the only trustworthy one left.
                        mirror.put(inRoom.toMirrored())
                        mirror.markReconciled(record.timerId)
                    } else {
                        val merged = inRoom.withRunStateOf(fromMirror)
                        if (merged != inRoom) {
                            timers.upsert(merged)
                            applied++
                        }
                        // After Room, so a pass interrupted in between repeats the same upsert next time.
                        mirror.markReconciled(record.timerId)
                    }
                }
            }
        }
        return TimerReconciliation.Done(applied, dropped)
    }

    private fun Timer.withRunStateOf(other: Timer): Timer =
        Timer.restore(id, length, label, ringtoneUri, evidenceEnabled, other.runState, createdAt)
}
