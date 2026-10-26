package dev.upyet.timer.scheduling

import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import dev.upyet.core.directboot.TimerMirrorReconciler
import dev.upyet.core.directboot.TimerReconciliation
import dev.upyet.core.directboot.toMirrored
import dev.upyet.testing.FakeTimerMirror
import dev.upyet.testing.FakeTimerMissedNotices
import dev.upyet.testing.FakeTimerRepository
import dev.upyet.testing.FakeTimerScheduler
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

/**
 * AGENTS.md §6a end to end below the receivers: boot, launch, unlock, expiry, wall-clock change and re-arm,
 * against fakes of the alarm, the notice, Room and the mirror. Every expected instant is worked by hand.
 */
class TimerDiscoveryTest {
    private val createdAt = Instant.parse("2026-10-26T07:00:00Z")

    /** Before the reboot: the anchor every stored timer was written with. */
    private val anchor = TimerAnchor(Duration.ofHours(5), Instant.parse("2026-10-26T08:00:00Z"))

    /** Ten minutes of wall-clock time later, two minutes into the new boot. */
    private val afterReboot = TimerAnchor(Duration.ofMinutes(2), Instant.parse("2026-10-26T08:10:00Z"))

    private var now = afterReboot
    private val scheduler = FakeTimerScheduler()
    private val notices = FakeTimerMissedNotices()
    private val handedOff = mutableListOf<TimerId>()

    private fun timer(id: Long, runState: TimerRunState) =
        Timer.restore(TimerId(id), Duration.ofMinutes(20), "Tea", null, false, runState, createdAt)

    private fun running(id: Long, remainingAtAnchor: Duration, at: TimerAnchor = anchor) =
        timer(id, TimerRunState.Running(remainingAtAnchor, at))

    private val roomNeverBuilt = Lazy<TimerRepository> { error("Room was built before first unlock") }

    private fun discovery(mirror: FakeTimerMirror, repository: Lazy<TimerRepository>, unlocked: Boolean): TimerDiscovery {
        val clock = { now }
        return TimerDiscovery(
            mirror = mirror,
            repository = repository,
            reconciler = TimerMirrorReconciler(mirror, repository, FakeUserUnlockState(unlocked), clock),
            clock = clock,
            alarms = TimerAlarmSync(scheduler, notices, clock),
            handoff = { handedOff += it },
        )
    }

    private fun lockedMirrorOf(vararg timers: Timer) = FakeTimerMirror(unlocked = false).apply { timers.forEach { put(it.toMirrored()) } }

    private fun roomOf(vararg timers: Timer): Pair<FakeTimerRepository, FakeTimerMirror> {
        val mirror = FakeTimerMirror(unlocked = true)
        timers.forEach { mirror.put(it.toMirrored()) }
        return FakeTimerRepository(timers.toList()) to mirror
    }

    // Boot, before first unlock: the mirror is the only store.

    @Test
    fun bootBeforeUnlockArmsTheMirroredTimerOnTheWallClockAndNeverBuildsRoom() = runTest {
        // 15 min left at 08:00, found at 08:10: 5 min left, zero at elapsed 7 min of the new boot.
        val mirror = lockedMirrorOf(running(1, Duration.ofMinutes(15)))

        discovery(mirror, roomNeverBuilt, unlocked = false).onBoot()

        assertThat(scheduler.armed.single()).isEqualTo(TimerAlarm(TimerId(1), Instant.parse("2026-10-26T08:15:00Z"), Duration.ofMinutes(7)))
        assertThat(mirror.all().single().writtenWhileLocked).isTrue()
        assertThat(handedOff).isEmpty()
    }

    @Test
    fun aTimerInsideTheGraceWindowRingsThroughAnImmediateAlarmNotFromTheReceiver() = runTest {
        // Zero at 08:09:30, found at 08:10: 30 s overdue, so it is armed for now rather than rung here.
        val mirror = lockedMirrorOf(running(1, Duration.ofSeconds(570)))

        discovery(mirror, roomNeverBuilt, unlocked = false).onBoot()

        assertThat(scheduler.armed.single()).isEqualTo(TimerAlarm(TimerId(1), Instant.parse("2026-10-26T08:10:00Z"), Duration.ofMinutes(2)))
        assertThat(handedOff).isEmpty()
        assertThat(notices.shown).isNull()
    }

    @Test
    fun aTimerMoreThanAMinuteOverdueIsMissedPostsTheNoticeAndNeverRings() = runTest {
        // Zero at 08:08:59, found at 08:10: 61 s overdue.
        val mirror = lockedMirrorOf(running(1, Duration.ofSeconds(539)))

        discovery(mirror, roomNeverBuilt, unlocked = false).onBoot()

        assertThat(mirror.all().single().state.name).isEqualTo("MISSED")
        assertThat(notices.shown?.map { it.id }).containsExactly(TimerId(1))
        assertThat(scheduler.armed).isEmpty()
        assertThat(scheduler.cancels).isAtLeast(1)
        assertThat(handedOff).isEmpty()
    }

    @Test
    fun exactlyOneAlarmIsArmedForTheEarliestEndingTimer() = runTest {
        val mirror =
            lockedMirrorOf(
                running(1, Duration.ofMinutes(30)),
                running(2, Duration.ofMinutes(12)),
                timer(3, TimerRunState.Paused(Duration.ofMinutes(1))),
            )

        discovery(mirror, roomNeverBuilt, unlocked = false).onBoot()

        // Timer 2: 12 min at 08:00, so zero at 08:12, two minutes into a boot that is two minutes old.
        assertThat(scheduler.armed.single()).isEqualTo(TimerAlarm(TimerId(2), Instant.parse("2026-10-26T08:12:00Z"), Duration.ofMinutes(4)))
    }

    // Launch and unlock: Room, after reconciliation.

    @Test
    fun launchReArmsFromRoomAndAgainArmsTheSameAlarm() = runTest {
        now = TimerAnchor(Duration.ofHours(5).plusMinutes(2), Instant.parse("2026-10-26T08:02:00Z"))
        val (repository, mirror) = roomOf(running(1, Duration.ofMinutes(5)))
        val discovery = discovery(mirror, { repository }, unlocked = true)

        discovery.rearm()
        discovery.rearm()

        val expected = TimerAlarm(TimerId(1), Instant.parse("2026-10-26T08:05:00Z"), Duration.ofHours(5).plusMinutes(5))
        assertThat(scheduler.armed).containsExactly(expected, expected)
    }

    @Test
    fun launchDoesNotRewriteARunningTimerWhoseAnchorIsStillGood() = runTest {
        now = TimerAnchor(Duration.ofHours(5).plusMinutes(2), Instant.parse("2026-10-26T08:02:00Z"))
        val stored = running(1, Duration.ofMinutes(5))
        val (repository, mirror) = roomOf(stored)

        discovery(mirror, { repository }, unlocked = true).rearm()

        assertThat(repository.upserts).isEqualTo(0)
        assertThat(repository.getTimer(TimerId(1))).isEqualTo(stored)
    }

    @Test
    fun unlockCopiesWhatBootDidIntoRoomBeforeDiscovering() = runTest {
        val stored = running(1, Duration.ofSeconds(539))
        val (repository, mirror) = roomOf(stored)
        mirror.unlocked = false
        discovery(mirror, roomNeverBuilt, unlocked = false).onBoot()
        mirror.unlocked = true

        val reconciliation = discovery(mirror, { repository }, unlocked = true).onUserUnlocked()

        assertThat(reconciliation).isEqualTo(TimerReconciliation.Done(applied = 1, dropped = 0))
        assertThat(repository.getTimer(TimerId(1))?.runState).isEqualTo(TimerRunState.Missed)
        assertThat(notices.shown?.map { it.id }).containsExactly(TimerId(1))
    }

    @Test
    fun resettingTheLastMissedTimerClearsTheNotice() = runTest {
        val (repository, mirror) = roomOf(running(1, Duration.ofSeconds(539)))
        val discovery = discovery(mirror, { repository }, unlocked = true)
        discovery.onBoot()
        assertThat(notices.shown).isNotNull()

        val missed = repository.getTimer(TimerId(1))!!
        repository.upsert((missed.reset() as TimerTransition.Applied).timer)
        discovery.rearm()

        assertThat(notices.shown).isNull()
    }

    @Test
    fun withoutExactAccessNothingIsArmedAndTheResultSaysSo() = runTest {
        scheduler.result = TimerArmResult.ExactAlarmsUnavailable
        val (repository, mirror) = roomOf(running(1, Duration.ofMinutes(15)))

        val report = discovery(mirror, { repository }, unlocked = true).onBoot()

        assertThat(report.arm).isEqualTo(TimerArmResult.ExactAlarmsUnavailable)
        assertThat(scheduler.armed).isEmpty()
    }

    // Expiry: a delivery is validated against the timer's current end instant.

    private val endsAt0805 = Instant.parse("2026-10-26T08:05:00Z")
    private val atEnd = TimerAnchor(Duration.ofHours(5).plusMinutes(5), endsAt0805)

    @Test
    fun aDeliveryForTheCurrentEndRingsAndArmsTheNextTimer() = runTest {
        now = atEnd
        val (repository, mirror) = roomOf(running(1, Duration.ofMinutes(5)), running(2, Duration.ofMinutes(9)))

        val result = discovery(mirror, { repository }, unlocked = true).onExpiry(TimerId(1), endsAt0805)

        assertThat(result).isEqualTo(TimerExpiry.RANG)
        assertThat(repository.getTimer(TimerId(1))?.runState).isEqualTo(TimerRunState.Ringing)
        assertThat(handedOff).containsExactly(TimerId(1))
        assertThat(scheduler.armed.last().timerId).isEqualTo(TimerId(2))
    }

    @Test
    fun aDeliveryWithAnotherEndInstantIsStale() = runTest {
        now = atEnd
        val (repository, mirror) = roomOf(running(1, Duration.ofMinutes(5)))

        val result = discovery(mirror, { repository }, unlocked = true).onExpiry(TimerId(1), Instant.parse("2026-10-26T08:04:00Z"))

        assertThat(result).isEqualTo(TimerExpiry.STALE)
        assertThat(repository.getTimer(TimerId(1))?.runState).isInstanceOf(TimerRunState.Running::class.java)
        assertThat(handedOff).isEmpty()
    }

    @Test
    fun aDeliveryForAPausedTimerIsStale() = runTest {
        now = atEnd
        val (repository, mirror) = roomOf(timer(1, TimerRunState.Paused(Duration.ofMinutes(2))))

        assertThat(discovery(mirror, { repository }, unlocked = true).onExpiry(TimerId(1), endsAt0805)).isEqualTo(TimerExpiry.STALE)
        assertThat(handedOff).isEmpty()
    }

    @Test
    fun aDeliveryForAnExtendedTimerIsStaleAndItsNewEndIsArmed() = runTest {
        now = atEnd
        val extended = (running(1, Duration.ofMinutes(5)).addMinute(anchor) as TimerTransition.Applied).timer
        val (repository, mirror) = roomOf(extended)

        val result = discovery(mirror, { repository }, unlocked = true).onExpiry(TimerId(1), endsAt0805)

        assertThat(result).isEqualTo(TimerExpiry.STALE)
        assertThat(handedOff).isEmpty()
        assertThat(scheduler.armed.last().endsAt).isEqualTo(Instant.parse("2026-10-26T08:06:00Z"))
    }

    @Test
    fun aDeliveryForADeletedTimerIsStale() = runTest {
        now = atEnd
        val (repository, mirror) = roomOf()

        assertThat(discovery(mirror, { repository }, unlocked = true).onExpiry(TimerId(1), endsAt0805)).isEqualTo(TimerExpiry.STALE)
        assertThat(handedOff).isEmpty()
    }

    @Test
    fun aDeliveryBeforeUnlockRingsFromTheMirror() = runTest {
        // Mirrored at 08:00 with 15 min left, so the alarm armed at boot carries 08:15.
        now = TimerAnchor(Duration.ofMinutes(7), Instant.parse("2026-10-26T08:15:00Z"))
        val mirror = lockedMirrorOf(running(1, Duration.ofMinutes(15)))

        val result = discovery(mirror, roomNeverBuilt, unlocked = false).onExpiry(TimerId(1), Instant.parse("2026-10-26T08:15:00Z"))

        assertThat(result).isEqualTo(TimerExpiry.RANG)
        assertThat(mirror.all().single().state.name).isEqualTo("RINGING")
        assertThat(handedOff).containsExactly(TimerId(1))
    }

    // The ringing store TimerPlaybackService writes through (slice #37).

    @Test
    fun theRingingStoreReturnsOnlyATimerStoredAsRinging() = runTest {
        val (repository, mirror) = roomOf(timer(1, TimerRunState.Ringing), running(2, Duration.ofMinutes(5)))
        val discovery = discovery(mirror, { repository }, unlocked = true)

        assertThat(discovery.ringing(TimerId(1))?.id).isEqualTo(TimerId(1))
        assertThat(discovery.ringing(TimerId(2))).isNull()
        assertThat(discovery.ringing(TimerId(3))).isNull()
    }

    @Test
    fun stopReturnsARingingTimerToIdleInRoom() = runTest {
        val (repository, mirror) = roomOf(timer(1, TimerRunState.Ringing))

        discovery(mirror, { repository }, unlocked = true).stop(TimerId(1))

        assertThat(repository.getTimer(TimerId(1))?.runState).isEqualTo(TimerRunState.Idle)
        assertThat(scheduler.armed).isEmpty()
    }

    @Test
    fun stopLeavesATimerThatIsNoLongerRingingAlone() = runTest {
        val runningTimer = running(1, Duration.ofMinutes(5))
        val (repository, mirror) = roomOf(runningTimer)

        discovery(mirror, { repository }, unlocked = true).stop(TimerId(1))

        assertThat(repository.getTimer(TimerId(1))).isEqualTo(runningTimer)
        assertThat(repository.upserts).isEqualTo(0)
    }

    @Test
    fun addMinuteRestartsARingingTimerAtSixtySecondsAndArmsIt() = runTest {
        // Found ringing at 08:10 / elapsed 2 min: Running with 60 s, so zero at 08:11, elapsed 3 min.
        val (repository, mirror) = roomOf(timer(1, TimerRunState.Ringing))

        discovery(mirror, { repository }, unlocked = true).addMinute(TimerId(1))

        assertThat(repository.getTimer(TimerId(1))?.runState).isEqualTo(TimerRunState.Running(Duration.ofSeconds(60), afterReboot))
        assertThat(scheduler.armed.single()).isEqualTo(TimerAlarm(TimerId(1), Instant.parse("2026-10-26T08:11:00Z"), Duration.ofMinutes(3)))
    }

    @Test
    fun stopAndAddMinuteBeforeUnlockWriteTheMirrorAndNeverBuildRoom() = runTest {
        val mirror = lockedMirrorOf(timer(1, TimerRunState.Ringing), timer(2, TimerRunState.Ringing))
        val discovery = discovery(mirror, roomNeverBuilt, unlocked = false)

        discovery.stop(TimerId(1))
        discovery.addMinute(TimerId(2))

        val states = mirror.all().associate { it.timerId to it.state.name }
        assertThat(states).containsExactly(TimerId(1), "IDLE", TimerId(2), "RUNNING")
        assertThat(scheduler.armed.single()).isEqualTo(TimerAlarm(TimerId(2), Instant.parse("2026-10-26T08:11:00Z"), Duration.ofMinutes(3)))
    }

    // Wall-clock change.

    @Test
    fun aWallClockChangeRecomputesRemainingTimeAndDoesNotMoveTheAlarm() = runTest {
        // Anchored at elapsed 5 h with 5 min left: zero at elapsed 5 h 5 min whatever the wall clock says.
        val (repository, mirror) = roomOf(running(1, Duration.ofMinutes(5)))
        now = TimerAnchor(Duration.ofHours(5).plusMinutes(2), Instant.parse("2026-10-26T09:02:00Z"))

        discovery(mirror, { repository }, unlocked = true).onWallClockChanged()

        assertThat(repository.getTimer(TimerId(1))?.remaining(now)).isEqualTo(Duration.ofMinutes(3))
        assertThat(scheduler.armed.single())
            .isEqualTo(TimerAlarm(TimerId(1), Instant.parse("2026-10-26T09:05:00Z"), Duration.ofHours(5).plusMinutes(5)))
    }

    @Test
    fun aWallClockChangeBeforeUnlockChangesNothing() = runTest {
        val mirror = lockedMirrorOf(running(1, Duration.ofMinutes(15)))
        val before = mirror.all()

        discovery(mirror, roomNeverBuilt, unlocked = false).onWallClockChanged()

        assertThat(mirror.all()).isEqualTo(before)
        assertThat(scheduler.armed).isEmpty()
    }
}
