package dev.upyet.stopwatch.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class StopwatchTest {
    private fun sec(n: Long): Duration = Duration.ofSeconds(n)

    @Test
    fun elapsedIsZeroWhenIdle() {
        assertThat(Stopwatch.IDLE.elapsedAt(sec(50))).isEqualTo(Duration.ZERO)
    }

    @Test
    fun elapsedRunsFromTheAnchor() {
        val running = Stopwatch(runningSince = sec(10))
        assertThat(running.elapsedAt(sec(25))).isEqualTo(sec(15))
    }

    @Test
    fun elapsedOfAPausedStopwatchIsItsAccumulatedTime() {
        val paused = Stopwatch(accumulated = sec(5))
        assertThat(paused.elapsedAt(sec(100))).isEqualTo(sec(5))
    }

    @Test
    fun elapsedClampsAClockReadBeforeTheAnchor() {
        val running = Stopwatch(accumulated = sec(5), runningSince = sec(10))
        assertThat(running.elapsedAt(sec(3))).isEqualTo(sec(5))
    }

    @Test
    fun startFromIdleSetsTheAnchorAndTheBoot() {
        val started = Stopwatch.IDLE.start(sec(7), boot = 2)
        assertThat(started.runningSince).isEqualTo(sec(7))
        assertThat(started.bootCount).isEqualTo(2)
    }

    @Test
    fun startingARunningStopwatchKeepsTheAnchor() {
        val first = Stopwatch.IDLE.start(sec(7), boot = 2)
        assertThat(first.start(sec(20), boot = 2)).isEqualTo(first)
    }

    @Test
    fun pauseFoldsTheCurrentRunIntoAccumulated() {
        val running = Stopwatch(runningSince = sec(10), bootCount = 1)
        val paused = running.pause(sec(25))
        assertThat(paused.accumulated).isEqualTo(sec(15))
        assertThat(paused.runningSince).isNull()
        assertThat(paused.bootCount).isEqualTo(1)
    }

    @Test
    fun resumeAfterPauseContinuesFromAccumulated() {
        val paused = Stopwatch(accumulated = sec(15), bootCount = 1)
        val resumed = paused.start(sec(100), boot = 1)
        assertThat(resumed.elapsedAt(sec(110))).isEqualTo(sec(25))
    }

    @Test
    fun pausingAPausedStopwatchChangesNothing() {
        val paused = Stopwatch(accumulated = sec(15), bootCount = 1)
        assertThat(paused.pause(sec(100))).isEqualTo(paused)
    }

    @Test
    fun lapOnlyTakesEffectWhileRunning() {
        assertThat(Stopwatch.IDLE.lap(sec(5))).isEqualTo(Stopwatch.IDLE)
        val paused = Stopwatch(accumulated = sec(15), bootCount = 1)
        assertThat(paused.lap(sec(100))).isEqualTo(paused)
    }

    @Test
    fun lapRowsSplitTheTotalsOldestFirst() {
        val running = Stopwatch(runningSince = sec(0))
            .lap(sec(10))
            .lap(sec(25))
            .lap(sec(45))
        assertThat(running.lapRows()).containsExactly(
            LapRow(number = 1, split = sec(10), total = sec(10)),
            LapRow(number = 2, split = sec(15), total = sec(25)),
            LapRow(number = 3, split = sec(20), total = sec(45)),
        ).inOrder()
    }

    @Test
    fun resetReturnsToIdle() {
        val running = Stopwatch(accumulated = sec(3), runningSince = sec(10), bootCount = 1, laps = listOf(sec(4)))
        assertThat(running.reset()).isEqualTo(Stopwatch.IDLE)
    }

    @Test
    fun reconcileKeepsASameBootStopwatch() {
        val running = Stopwatch(runningSince = sec(10), bootCount = 1)
        assertThat(running.reconcile(sec(20), boot = 1)).isEqualTo(running)
    }

    @Test
    fun reconcileResetsARunningStopwatchFromAnotherBoot() {
        val running = Stopwatch(runningSince = sec(10), bootCount = 1)
        assertThat(running.reconcile(sec(20), boot = 2)).isEqualTo(Stopwatch.IDLE)
    }

    @Test
    fun reconcileResetsAPausedStopwatchFromAnotherBoot() {
        val paused = Stopwatch(accumulated = sec(5), bootCount = 1)
        assertThat(paused.reconcile(sec(20), boot = 2)).isEqualTo(Stopwatch.IDLE)
    }

    @Test
    fun reconcileResetsWhenBootIsUnknownAndTheClockWentBack() {
        val withBoot = Stopwatch(runningSince = sec(10), bootCount = 1)
        assertThat(withBoot.reconcile(sec(5), boot = null)).isEqualTo(Stopwatch.IDLE)

        val withoutBoot = Stopwatch(runningSince = sec(10), bootCount = null)
        assertThat(withoutBoot.reconcile(sec(5), boot = null)).isEqualTo(Stopwatch.IDLE)
    }

    @Test
    fun reconcileKeepsAStopwatchWithUnknownBootWhenTheClockHasNotGoneBack() {
        val withoutBoot = Stopwatch(runningSince = sec(10), bootCount = null)
        assertThat(withoutBoot.reconcile(sec(10), boot = null)).isEqualTo(withoutBoot)
        assertThat(withoutBoot.reconcile(sec(20), boot = null)).isEqualTo(withoutBoot)
    }

    @Test
    fun reconcileLeavesIdleAlone() {
        assertThat(Stopwatch.IDLE.reconcile(sec(5), boot = 2)).isEqualTo(Stopwatch.IDLE)
    }
}
