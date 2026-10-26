package dev.upyet.timer.scheduling

import com.google.common.truth.Truth.assertThat
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** ADR 0008: one exact alarm, for whichever Running timer ends first. Expected values are worked by hand. */
class TimerAlarmTest {
    private val anchor = TimerAnchor(Duration.ofMinutes(10), Instant.parse("2026-10-26T08:00:00Z"))
    private val now = TimerAnchor(Duration.ofMinutes(12), Instant.parse("2026-10-26T08:02:00Z"))

    private fun timer(id: Long, runState: TimerRunState) =
        Timer.restore(TimerId(id), Duration.ofMinutes(30), "Tea", null, false, runState, anchor.wallClock)

    private fun running(id: Long, remainingAtAnchor: Duration, at: TimerAnchor = anchor) =
        timer(id, TimerRunState.Running(remainingAtAnchor, at))

    @Test
    fun noRunningTimerMeansNoAlarm() {
        val timers = listOf(
            timer(1, TimerRunState.Idle),
            timer(2, TimerRunState.Paused(Duration.ofMinutes(1))),
            timer(3, TimerRunState.Ringing),
            timer(4, TimerRunState.Missed),
        )

        assertThat(TimerAlarm.next(timers, now)).isNull()
    }

    @Test
    fun theEarliestEndingRunningTimerGetsTheAlarm() {
        val timers = listOf(running(1, Duration.ofMinutes(9)), running(2, Duration.ofMinutes(5)), running(3, Duration.ofMinutes(7)))

        val alarm = TimerAlarm.next(timers, now)

        // Anchored at elapsed 10 min with 5 min left: zero at elapsed 15 min, wall 08:05.
        assertThat(alarm).isEqualTo(TimerAlarm(TimerId(2), Instant.parse("2026-10-26T08:05:00Z"), Duration.ofMinutes(15)))
    }

    @Test
    fun aPausedTimerEndingSoonerDoesNotTakeTheAlarm() {
        val timers = listOf(timer(1, TimerRunState.Paused(Duration.ofSeconds(1))), running(2, Duration.ofMinutes(5)))

        assertThat(TimerAlarm.next(timers, now)?.timerId).isEqualTo(TimerId(2))
    }

    @Test
    fun anOverdueTimerIsArmedForNow() {
        // Zero at elapsed 11 min, one minute before now: the alarm fires immediately.
        val alarm = TimerAlarm.next(listOf(running(1, Duration.ofMinutes(1))), now)

        assertThat(alarm?.triggerAtElapsed).isEqualTo(Duration.ofMinutes(12))
        assertThat(alarm?.endsAt).isEqualTo(Instant.parse("2026-10-26T08:01:00Z"))
    }

    @Test
    fun aTieGoesToTheLowerIdSoReArmingIsDeterministic() {
        val timers = listOf(running(7, Duration.ofMinutes(5)), running(3, Duration.ofMinutes(5)))

        assertThat(TimerAlarm.next(timers, now)?.timerId).isEqualTo(TimerId(3))
        assertThat(TimerAlarm.next(timers.reversed(), now)?.timerId).isEqualTo(TimerId(3))
    }

    @Test
    fun anAnchorFromBeforeARebootCountsOnTheWallClock() {
        // Anchored at elapsed 5 h in the previous boot; now is 2 min of elapsed time later on the wall clock.
        val previousBoot = TimerAnchor(Duration.ofHours(5), Instant.parse("2026-10-26T08:00:00Z"))

        val alarm = TimerAlarm.next(listOf(running(1, Duration.ofMinutes(5), previousBoot)), now)

        assertThat(alarm?.triggerAtElapsed).isEqualTo(Duration.ofMinutes(15))
    }
}
