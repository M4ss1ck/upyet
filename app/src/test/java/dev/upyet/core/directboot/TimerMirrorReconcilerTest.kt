package dev.upyet.core.directboot

import com.google.common.truth.Truth.assertThat
import dev.upyet.testing.FakeTimerMirror
import dev.upyet.testing.FakeTimerRepository
import dev.upyet.testing.FakeUserUnlockState
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRepository
import dev.upyet.timer.domain.TimerRunState
import dev.upyet.timer.domain.TimerTransition
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimerMirrorReconcilerTest {
    private val createdAt = Instant.parse("2026-10-26T07:00:00Z")
    private val beforeReboot = TimerAnchor(Duration.ofHours(5), Instant.parse("2026-10-26T08:00:00Z"))

    /** After the reboot: elapsed restarted, the wall clock kept going. */
    private val unlockedAt = TimerAnchor(Duration.ofMinutes(3), Instant.parse("2026-10-26T08:20:00Z"))

    private val mirror = FakeTimerMirror()

    private fun timer(id: Long, runState: TimerRunState = TimerRunState.Idle, label: String = "Pasta", evidence: Boolean = true) =
        Timer.restore(TimerId(id), Duration.ofMinutes(10), label, null, evidence, runState, createdAt)

    private fun reconciler(repository: TimerRepository, unlocked: Boolean = true) = TimerMirrorReconciler(
        mirror = mirror,
        repository = { repository },
        userUnlockState = FakeUserUnlockState(unlocked),
        clock = { unlockedAt },
    )

    /** What the pre-unlock path (slice #35) does: the mirror changes while Room cannot be reached. */
    private fun writeWhileLocked(timer: Timer) {
        mirror.unlocked = false
        mirror.put(timer.toMirrored())
        mirror.unlocked = true
    }

    @Test
    fun aRunStateChangedBeforeUnlockReachesRoom() = runTest {
        val running = timer(1, TimerRunState.Running(Duration.ofMinutes(5), beforeReboot))
        val repository = FakeTimerRepository(listOf(running))
        mirror.put(running.toMirrored())
        writeWhileLocked(timer(1, TimerRunState.Ringing))

        val result = reconciler(repository).reconcile()

        assertThat(repository.getTimer(TimerId(1))?.runState).isEqualTo(TimerRunState.Ringing)
        assertThat(result).isEqualTo(TimerReconciliation.Done(applied = 1, dropped = 0))
        assertThat(mirror.all().single().writtenWhileLocked).isFalse()
    }

    @Test
    fun aRunningTimerFromTheMirrorKeepsItsWallClockEndInRoom() = runTest {
        val repository = FakeTimerRepository(listOf(timer(1)))
        // +1 min from the ringing screen before unlock: one minute from 08:15, so it ends at 08:16.
        val restarted = TimerAnchor(Duration.ofMinutes(1), Instant.parse("2026-10-26T08:15:00Z"))
        writeWhileLocked(timer(1, TimerRunState.Running(Duration.ofMinutes(1), restarted)))

        reconciler(repository).reconcile()

        val stored = checkNotNull(repository.getTimer(TimerId(1)))
        val state = stored.runState as TimerRunState.Running
        assertThat(state.anchor.wallClock).isEqualTo(restarted.wallClock)
        assertThat(state.remainingAtAnchor).isEqualTo(Duration.ofMinutes(1))
        // At 08:20 it is four minutes overdue, which discovery (slice #35) must still be able to see.
        val overdue = stored.onDiscovery(unlockedAt, afterBoot = false)
        assertThat((overdue as TimerTransition.Applied).timer.runState).isEqualTo(TimerRunState.Missed)
    }

    @Test
    fun reconcilingTwiceChangesNothingTheSecondTime() = runTest {
        val repository = FakeTimerRepository(listOf(timer(1), timer(2)))
        writeWhileLocked(timer(1, TimerRunState.Ringing))

        reconciler(repository).reconcile()
        val afterFirst = repository.all
        val upsertsAfterFirst = repository.upserts
        val second = reconciler(repository).reconcile()

        assertThat(repository.all).isEqualTo(afterFirst)
        assertThat(repository.all.map { it.id }).containsExactly(TimerId(1), TimerId(2))
        assertThat(repository.upserts).isEqualTo(upsertsAfterFirst)
        assertThat(second).isEqualTo(TimerReconciliation.Done(applied = 0, dropped = 0))
    }

    @Test
    fun anEditMadeAfterUnlockIsNotRevertedByASecondReconcile() = runTest {
        val repository = FakeTimerRepository(listOf(timer(1)))
        writeWhileLocked(timer(1, TimerRunState.Ringing))
        reconciler(repository).reconcile()

        // The user stops it and renames it after unlock; the mirror still says Ringing.
        repository.upsert(timer(1, TimerRunState.Idle, label = "Tea"))
        reconciler(repository).reconcile()

        assertThat(repository.getTimer(TimerId(1))).isEqualTo(timer(1, TimerRunState.Idle, label = "Tea"))
    }

    /** The pending mark is sticky: a mirror write after unlock but before reconciling cannot clear it. */
    @Test
    fun aWriteAfterUnlockDoesNotHideAPendingRecord() = runTest {
        val repository = FakeTimerRepository(listOf(timer(1)))
        writeWhileLocked(timer(1, TimerRunState.Ringing))
        // After unlock, before USER_UNLOCKED is handled, something mirrors a newer run state.
        mirror.put(timer(1, TimerRunState.Missed).toMirrored())

        reconciler(repository).reconcile()

        assertThat(repository.getTimer(TimerId(1))?.runState).isEqualTo(TimerRunState.Missed)
        assertThat(mirror.all().single().writtenWhileLocked).isFalse()
    }

    @Test
    fun aRecordNotWrittenWhileLockedNeverOverwritesRoom() = runTest {
        val repository = FakeTimerRepository(listOf(timer(1, TimerRunState.Paused(Duration.ofSeconds(30)))))
        mirror.put(timer(1, TimerRunState.Ringing).toMirrored())

        reconciler(repository).reconcile()

        assertThat(repository.getTimer(TimerId(1))?.runState).isEqualTo(TimerRunState.Paused(Duration.ofSeconds(30)))
        assertThat(repository.upserts).isEqualTo(0)
    }

    @Test
    fun roomKeepsTheLabelLengthAndEvidenceFlag() = runTest {
        val repository = FakeTimerRepository(listOf(timer(1, label = "Pasta", evidence = false)))
        writeWhileLocked(timer(1, TimerRunState.Ringing, label = "stale", evidence = true))

        reconciler(repository).reconcile()

        assertThat(repository.getTimer(TimerId(1))).isEqualTo(timer(1, TimerRunState.Ringing, label = "Pasta", evidence = false))
    }

    @Test
    fun aMirrorRecordForATimerRoomDoesNotHaveIsDroppedNotCreated() = runTest {
        val repository = FakeTimerRepository(listOf(timer(1)))
        writeWhileLocked(timer(9, TimerRunState.Ringing))

        val result = reconciler(repository).reconcile()

        assertThat(repository.all.map { it.id }).containsExactly(TimerId(1))
        assertThat(mirror.all()).isEmpty()
        assertThat(result).isEqualTo(TimerReconciliation.Done(applied = 0, dropped = 1))
    }

    @Test
    fun anInconsistentRecordIsReplacedByRoomsCopy() = runTest {
        val stored = timer(1, TimerRunState.Paused(Duration.ofSeconds(30)))
        val repository = FakeTimerRepository(listOf(stored))
        mirror.unlocked = false
        mirror.put(timer(1, TimerRunState.Running(Duration.ofMinutes(1), beforeReboot)).toMirrored().copy(wallClockAnchor = null))
        mirror.unlocked = true

        reconciler(repository).reconcile()

        assertThat(repository.getTimer(TimerId(1))).isEqualTo(stored)
        assertThat(mirror.all().single()).isEqualTo(stored.toMirrored())
    }

    @Test
    fun beforeUnlockNothingTouchesRoom() = runTest {
        writeWhileLocked(timer(1, TimerRunState.Ringing))
        val reconciler = TimerMirrorReconciler(
            mirror = mirror,
            repository = { error("Room must not be built before first unlock") },
            userUnlockState = FakeUserUnlockState(unlocked = false),
            clock = { unlockedAt },
        )

        assertThat(reconciler.reconcile()).isEqualTo(TimerReconciliation.Locked)
        assertThat(mirror.all().single().writtenWhileLocked).isTrue()
    }
}
