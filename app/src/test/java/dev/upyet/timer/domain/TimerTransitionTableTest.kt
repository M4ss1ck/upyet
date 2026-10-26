package dev.upyet.timer.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * One test per cell of ADR 0008's transition table, plus the clock-change, discovery and round-trip rules.
 * Every expected value is a literal written from the table, never recomputed from the code under test.
 */
class TimerTransitionTableTest {
    private val anchor = TimerAnchor(Duration.ofMinutes(10), Instant.parse("2026-10-26T08:00:00Z"))
    private val now = TimerAnchor(Duration.ofMinutes(12), Instant.parse("2026-10-26T08:02:00Z"))

    private val idle = TimerRunState.Idle
    private val running = TimerRunState.Running(Duration.ofMinutes(4), anchor)
    private val paused = TimerRunState.Paused(Duration.ofMinutes(3))
    private val ringing = TimerRunState.Ringing
    private val missed = TimerRunState.Missed

    private fun timerIn(state: TimerRunState) = Timer.restore(
        id = TimerId(1),
        length = Duration.ofMinutes(5),
        label = "Tea",
        ringtoneUri = null,
        evidenceEnabled = true,
        runState = state,
        createdAt = anchor.wallClock,
    )

    private fun TimerTransition.appliedTimer(): Timer {
        assertThat(this).isInstanceOf(TimerTransition.Applied::class.java)
        return (this as TimerTransition.Applied).timer
    }

    private fun TimerTransition.appliedState(): TimerRunState = appliedTimer().runState

    // start(now)

    @Test
    fun startFromIdleRunsForTheFullLength() {
        val result = timerIn(idle).start(now)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Running(Duration.ofMinutes(5), now))
    }

    @Test
    fun startWhileRunningIsNoOp() {
        assertThat(timerIn(running).start(now)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun startFromPausedResumesWithWhatWasLeft() {
        val result = timerIn(paused).start(now)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Running(Duration.ofMinutes(3), now))
    }

    @Test
    fun startWhileRingingIsNoOp() {
        assertThat(timerIn(ringing).start(now)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun startWhileMissedIsNoOp() {
        assertThat(timerIn(missed).start(now)).isEqualTo(TimerTransition.NoOp)
    }

    // pause(now)

    @Test
    fun pauseWhileIdleIsNoOp() {
        assertThat(timerIn(idle).pause(now)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun pauseWhileRunningFreezesTwoMinutesLeft() {
        val result = timerIn(running).pause(now)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Paused(Duration.ofMinutes(2)))
    }

    @Test
    fun pauseWhilePausedIsNoOp() {
        assertThat(timerIn(paused).pause(now)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun pauseWhileRingingIsNotOffered() {
        assertThat(timerIn(ringing).pause(now)).isEqualTo(TimerTransition.NotOffered)
    }

    @Test
    fun pauseWhileMissedIsNotOffered() {
        assertThat(timerIn(missed).pause(now)).isEqualTo(TimerTransition.NotOffered)
    }

    // reset()

    @Test
    fun resetWhileIdleIsNoOp() {
        assertThat(timerIn(idle).reset()).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun resetWhileRunningReturnsToIdleAtFullLength() {
        val result = timerIn(running).reset().appliedTimer()

        assertThat(result.runState).isEqualTo(TimerRunState.Idle)
        assertThat(result.length).isEqualTo(Duration.ofMinutes(5))
        assertThat(result.remaining(now)).isEqualTo(Duration.ofMinutes(5))
    }

    @Test
    fun resetWhilePausedReturnsToIdleAtFullLength() {
        val result = timerIn(paused).reset().appliedTimer()

        assertThat(result.runState).isEqualTo(TimerRunState.Idle)
        assertThat(result.length).isEqualTo(Duration.ofMinutes(5))
        assertThat(result.remaining(now)).isEqualTo(Duration.ofMinutes(5))
    }

    @Test
    fun resetWhileRingingReturnsToIdleAtFullLength() {
        val result = timerIn(ringing).reset().appliedTimer()

        assertThat(result.runState).isEqualTo(TimerRunState.Idle)
        assertThat(result.length).isEqualTo(Duration.ofMinutes(5))
        assertThat(result.remaining(now)).isEqualTo(Duration.ofMinutes(5))
    }

    @Test
    fun resetWhileMissedReturnsToIdleAtFullLength() {
        val result = timerIn(missed).reset().appliedTimer()

        assertThat(result.runState).isEqualTo(TimerRunState.Idle)
        assertThat(result.length).isEqualTo(Duration.ofMinutes(5))
        assertThat(result.remaining(now)).isEqualTo(Duration.ofMinutes(5))
    }

    // addMinute(now)

    @Test
    fun addMinuteWhileIdleIsNotOffered() {
        assertThat(timerIn(idle).addMinute(now)).isEqualTo(TimerTransition.NotOffered)
    }

    @Test
    fun addMinuteWhileRunningAddsOneMinuteToRemaining() {
        val result = timerIn(running).addMinute(now).appliedTimer()

        assertThat(result.runState).isEqualTo(TimerRunState.Running(Duration.ofMinutes(3), now))
        assertThat(result.length).isEqualTo(Duration.ofMinutes(5))
    }

    @Test
    fun addMinuteWhilePausedAddsOneMinuteToRemaining() {
        val result = timerIn(paused).addMinute(now)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Paused(Duration.ofMinutes(4)))
    }

    @Test
    fun addMinuteWhileRingingRestartsAtOneMinute() {
        val result = timerIn(ringing).addMinute(now)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Running(Duration.ofMinutes(1), now))
    }

    @Test
    fun addMinuteWhileMissedRestartsAtOneMinute() {
        val result = timerIn(missed).addMinute(now)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Running(Duration.ofMinutes(1), now))
    }

    // onExpiry(endsAt): the running fixture is anchored at 08:00 with 4 min left, so its own delivery says 08:04.

    private val runningEndsAt = Instant.parse("2026-10-26T08:04:00Z")

    @Test
    fun onExpiryWhileIdleIsNoOp() {
        assertThat(timerIn(idle).onExpiry(runningEndsAt)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun onExpiryWhileRunningStartsRinging() {
        assertThat(timerIn(running).onExpiry(runningEndsAt).appliedState()).isEqualTo(TimerRunState.Ringing)
    }

    @Test
    fun onExpiryWithAnotherEndInstantIsStale() {
        val delivered = Instant.parse("2026-10-26T08:03:59Z")

        assertThat(timerIn(running).onExpiry(delivered)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun onExpiryAfterPlusOneMinuteIsStale() {
        val extended = timerIn(running).addMinute(now).appliedTimer()

        assertThat(extended.onExpiry(runningEndsAt)).isEqualTo(TimerTransition.NoOp)
        assertThat(extended.endsAt).isEqualTo(Instant.parse("2026-10-26T08:05:00Z"))
    }

    @Test
    fun onExpiryAfterPauseAndResumeIsStale() {
        val later = TimerAnchor(Duration.ofMinutes(13), Instant.parse("2026-10-26T08:03:00Z"))
        val resumed = timerIn(running).pause(now).appliedTimer().start(later).appliedTimer()

        assertThat(resumed.onExpiry(runningEndsAt)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun onExpiryWhilePausedIsNoOp() {
        assertThat(timerIn(paused).onExpiry(runningEndsAt)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun onExpiryWhileRingingIsNoOp() {
        assertThat(timerIn(ringing).onExpiry(runningEndsAt)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun onExpiryWhileMissedIsNoOp() {
        assertThat(timerIn(missed).onExpiry(runningEndsAt)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun onlyARunningTimerHasAnEndInstant() {
        assertThat(timerIn(running).endsAt).isEqualTo(runningEndsAt)
        assertThat(timerIn(paused).endsAt).isNull()
        assertThat(timerIn(ringing).endsAt).isNull()
    }

    // stop()

    @Test
    fun stopWhileIdleIsNoOp() {
        assertThat(timerIn(idle).stop()).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun stopWhileRunningIsNoOp() {
        assertThat(timerIn(running).stop()).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun stopWhilePausedIsNoOp() {
        assertThat(timerIn(paused).stop()).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun stopWhileRingingReturnsToIdle() {
        assertThat(timerIn(ringing).stop().appliedState()).isEqualTo(TimerRunState.Idle)
    }

    @Test
    fun stopWhileMissedIsNoOp() {
        assertThat(timerIn(missed).stop()).isEqualTo(TimerTransition.NoOp)
    }

    // delete()

    @Test
    fun deleteWhileIdleRemovesTheTimer() {
        assertThat(timerIn(idle).delete()).isEqualTo(TimerTransition.Removed)
    }

    @Test
    fun deleteWhileRunningRemovesTheTimer() {
        assertThat(timerIn(running).delete()).isEqualTo(TimerTransition.Removed)
    }

    @Test
    fun deleteWhilePausedRemovesTheTimer() {
        assertThat(timerIn(paused).delete()).isEqualTo(TimerTransition.Removed)
    }

    @Test
    fun deleteWhileRingingRemovesTheTimer() {
        assertThat(timerIn(ringing).delete()).isEqualTo(TimerTransition.Removed)
    }

    @Test
    fun deleteWhileMissedRemovesTheTimer() {
        assertThat(timerIn(missed).delete()).isEqualTo(TimerTransition.Removed)
    }

    // onWallClockChanged(now)

    @Test
    fun wallClockChangeWhileIdleIsNoOp() {
        assertThat(timerIn(idle).onWallClockChanged(now)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun wallClockChangeWhileRunningReanchorsFromElapsedDelta() {
        val result = timerIn(running).onWallClockChanged(now)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Running(Duration.ofMinutes(2), now))
    }

    @Test
    fun wallClockChangeWhilePausedIsNoOp() {
        assertThat(timerIn(paused).onWallClockChanged(now)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun wallClockChangeWhileRingingIsNoOp() {
        assertThat(timerIn(ringing).onWallClockChanged(now)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun wallClockChangeWhileMissedIsNoOp() {
        assertThat(timerIn(missed).onWallClockChanged(now)).isEqualTo(TimerTransition.NoOp)
    }

    // onTimeZoneChanged()

    @Test
    fun timeZoneChangeWhileIdleIsNoOp() {
        assertThat(timerIn(idle).onTimeZoneChanged()).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun timeZoneChangeWhileRunningIsNoOp() {
        assertThat(timerIn(running).onTimeZoneChanged()).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun timeZoneChangeWhilePausedIsNoOp() {
        assertThat(timerIn(paused).onTimeZoneChanged()).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun timeZoneChangeWhileRingingIsNoOp() {
        assertThat(timerIn(ringing).onTimeZoneChanged()).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun timeZoneChangeWhileMissedIsNoOp() {
        assertThat(timerIn(missed).onTimeZoneChanged()).isEqualTo(TimerTransition.NoOp)
    }

    // onDiscovery(now, afterBoot = false)

    @Test
    fun discoveryWhileIdleIsNoOp() {
        assertThat(timerIn(idle).onDiscovery(now, afterBoot = false)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun discoveryWhileRunningRecomputesRemaining() {
        val result = timerIn(running).onDiscovery(now, afterBoot = false)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Running(Duration.ofMinutes(2), now))
    }

    @Test
    fun discoveryWhilePausedIsNoOp() {
        assertThat(timerIn(paused).onDiscovery(now, afterBoot = false)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun discoveryWhileRingingStillRings() {
        val result = timerIn(ringing).onDiscovery(now, afterBoot = false)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Ringing)
    }

    @Test
    fun discoveryWhileMissedIsNoOp() {
        assertThat(timerIn(missed).onDiscovery(now, afterBoot = false)).isEqualTo(TimerTransition.NoOp)
    }

    // Extra: discovery and missed threshold

    @Test
    fun discoveryMoreThanSixtySecondsOverdueBecomesMissed() {
        val overdue = TimerAnchor(anchor.elapsedRealtime.plusSeconds(91), anchor.wallClock.plusSeconds(91))
        val result = timerIn(TimerRunState.Running(Duration.ofSeconds(30), anchor)).onDiscovery(overdue, afterBoot = false)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Missed)
    }

    @Test
    fun discoveryExactlySixtySecondsOverdueStillRings() {
        val overdue = TimerAnchor(anchor.elapsedRealtime.plusSeconds(90), anchor.wallClock.plusSeconds(90))
        val result = timerIn(TimerRunState.Running(Duration.ofSeconds(30), anchor)).onDiscovery(overdue, afterBoot = false)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Running(Duration.ZERO, overdue))
    }

    @Test
    fun discoveryOneSecondOverdueRingsLate() {
        val overdue = TimerAnchor(anchor.elapsedRealtime.plusSeconds(31), anchor.wallClock.plusSeconds(31))
        val result = timerIn(TimerRunState.Running(Duration.ofSeconds(30), anchor)).onDiscovery(overdue, afterBoot = false)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Running(Duration.ZERO, overdue))
    }

    // Extra: discovery after boot uses the wall clock

    @Test
    fun discoveryAfterBootUsesWallClockEvenWhenElapsedDeltaIsPositive() {
        val afterReboot = TimerAnchor(Duration.ofMinutes(30), anchor.wallClock.plusSeconds(60))
        val result = timerIn(running).onDiscovery(afterReboot, afterBoot = true)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Running(Duration.ofMinutes(3), afterReboot))
    }

    @Test
    fun discoveryNotAfterBootTrustsPositiveElapsedDeltaAndMissesTheTimer() {
        val afterReboot = TimerAnchor(Duration.ofMinutes(30), anchor.wallClock.plusSeconds(60))
        val result = timerIn(running).onDiscovery(afterReboot, afterBoot = false)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Missed)
    }

    // Extra: discovery after reboot with a negative elapsed delta

    @Test
    fun discoveryWithNegativeElapsedDeltaFallsBackToWallClock() {
        val rebooted = TimerAnchor(Duration.ofSeconds(15), anchor.wallClock.plusSeconds(60))
        val result = timerIn(running).onDiscovery(rebooted, afterBoot = false)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Running(Duration.ofMinutes(3), rebooted))
    }

    // Extra: wall clock changes do not move a running timer's end on the elapsed clock

    @Test
    fun wallClockMovedForwardAnHourDoesNotMoveRunningTimer() {
        val moved = TimerAnchor(anchor.elapsedRealtime.plusSeconds(60), anchor.wallClock.plusSeconds(3660))
        val result = timerIn(running).onWallClockChanged(moved).appliedTimer()

        assertThat(result.runState).isEqualTo(TimerRunState.Running(Duration.ofMinutes(3), moved))
        val newState = result.runState as TimerRunState.Running
        assertThat(newState.anchor.elapsedRealtime.plus(newState.remainingAtAnchor)).isEqualTo(Duration.ofMinutes(14))
    }

    @Test
    fun wallClockMovedBackwardAnHourDoesNotMoveRunningTimer() {
        val moved = TimerAnchor(anchor.elapsedRealtime.plusSeconds(60), anchor.wallClock.minusSeconds(3540))
        val result = timerIn(running).onWallClockChanged(moved)

        assertThat(result.appliedState()).isEqualTo(TimerRunState.Running(Duration.ofMinutes(3), moved))
    }

    @Test
    fun negativeElapsedDeltaOnWallClockChangeIsIgnored() {
        val moved = TimerAnchor(Duration.ofSeconds(5), anchor.wallClock.plusSeconds(60))

        assertThat(timerIn(running).onWallClockChanged(moved)).isEqualTo(TimerTransition.NoOp)
    }

    @Test
    fun timeZoneChangeLeavesRunningTimerRemainingUnchanged() {
        assertThat(timerIn(running).onTimeZoneChanged()).isEqualTo(TimerTransition.NoOp)
        assertThat(timerIn(running).remaining(now)).isEqualTo(Duration.ofMinutes(2))
    }

    // Extra: round trip

    @Test
    fun startThenExpiryThenStopReturnsToIdleAtFullLength() {
        val started = timerIn(idle).start(anchor).appliedTimer()
        val ringingTimer = started.onExpiry(Instant.parse("2026-10-26T08:05:00Z")).appliedTimer()
        val stopped = ringingTimer.stop().appliedTimer()

        assertThat(stopped.runState).isEqualTo(TimerRunState.Idle)
        assertThat(stopped.remaining(now)).isEqualTo(Duration.ofMinutes(5))
    }
}
