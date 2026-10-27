package dev.upyet.timer.scheduling

import com.google.common.truth.Truth.assertThat
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** ADR 0012: only a Running timer shows on the running notice, the one that ends first, ties by id. */
class RunningNoticeTest {
    private val anchor = TimerAnchor(Duration.ofHours(1), Instant.parse("2026-10-26T08:00:00Z"))

    private fun timer(id: Long, state: TimerRunState) =
        Timer.restore(TimerId(id), Duration.ofMinutes(10), "Tea", null, false, state, Instant.EPOCH)

    /** Running with [remaining] left at [anchor], so it ends at 08:00 plus [remaining]. */
    private fun running(id: Long, remaining: Duration) = timer(id, TimerRunState.Running(remaining, anchor))

    @Test
    fun noTimersCancels() {
        assertThat(RunningNotice.decide(emptyList())).isEqualTo(RunningNotice.Cancel)
    }

    @Test
    fun pausedRingingMissedAndIdleNeverShow() {
        val timers = listOf(
            timer(1, TimerRunState.Paused(Duration.ofMinutes(4))),
            timer(2, TimerRunState.Ringing),
            timer(3, TimerRunState.Missed),
            timer(4, TimerRunState.Idle),
        )

        assertThat(RunningNotice.decide(timers)).isEqualTo(RunningNotice.Cancel)
    }

    @Test
    fun aSingleRunningTimerShowsWithNoOthers() {
        val timer = running(1, Duration.ofMinutes(5))

        assertThat(RunningNotice.decide(listOf(timer)))
            .isEqualTo(RunningNotice.Show(timer, Instant.parse("2026-10-26T08:05:00Z"), alsoRunning = 0))
    }

    @Test
    fun aPausedTimerIsIgnoredWhenAnotherIsRunning() {
        val runningTimer = running(2, Duration.ofMinutes(10))
        val notice = RunningNotice.decide(listOf(timer(1, TimerRunState.Paused(Duration.ofMinutes(1))), runningTimer))

        assertThat(notice).isEqualTo(RunningNotice.Show(runningTimer, Instant.parse("2026-10-26T08:10:00Z"), alsoRunning = 0))
    }

    @Test
    fun theRunningTimerThatEndsFirstIsShownAndTheRestAreCounted() {
        val soonest = running(2, Duration.ofMinutes(3))
        val notice = RunningNotice.decide(listOf(running(1, Duration.ofMinutes(10)), soonest, running(3, Duration.ofMinutes(7))))

        assertThat(notice).isEqualTo(RunningNotice.Show(soonest, Instant.parse("2026-10-26T08:03:00Z"), alsoRunning = 2))
    }

    @Test
    fun aTieGoesToTheSmallerId() {
        val lower = running(3, Duration.ofMinutes(5))
        val notice = RunningNotice.decide(listOf(running(7, Duration.ofMinutes(5)), lower))

        assertThat(notice).isEqualTo(RunningNotice.Show(lower, Instant.parse("2026-10-26T08:05:00Z"), alsoRunning = 1))
    }
}
