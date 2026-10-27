package dev.upyet.timer.scheduling

import com.google.common.truth.Truth.assertThat
import dev.upyet.testing.FakeTimerMissedNotices
import dev.upyet.testing.FakeTimerRunningNotices
import dev.upyet.testing.FakeTimerScheduler
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** ADR 0012: every re-arm brings the running notice in line with the timers, as it does the alarm and the notice. */
class TimerAlarmSyncTest {
    private val now = TimerAnchor(Duration.ZERO, Instant.parse("2026-10-26T08:00:00Z"))
    private val running = FakeTimerRunningNotices()
    private val sync = TimerAlarmSync(FakeTimerScheduler(), FakeTimerMissedNotices(), { now }, running)

    private fun timer(state: TimerRunState) = Timer.restore(TimerId(1), Duration.ofMinutes(10), "Pasta", null, false, state, Instant.EPOCH)

    @Test
    fun pausingTheOnlyRunningTimerCancelsTheNoticeAndStartingItShowsItAgain() = runTest {
        val runningTimer = timer(TimerRunState.Running(Duration.ofMinutes(5), now))

        sync.sync { listOf(runningTimer) }
        assertThat(running.shown?.timer).isEqualTo(runningTimer)
        assertThat(running.shown?.endsAt).isEqualTo(Instant.parse("2026-10-26T08:05:00Z"))

        sync.sync { listOf(timer(TimerRunState.Paused(Duration.ofMinutes(5)))) }
        assertThat(running.shown).isNull()
        assertThat(running.cancels).isEqualTo(1)

        sync.sync { listOf(runningTimer) }
        assertThat(running.shown?.timer).isEqualTo(runningTimer)
    }
}
