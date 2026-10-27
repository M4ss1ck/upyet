package dev.upyet.timer.ui

import com.google.common.truth.Truth.assertThat
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimerListTest {
    private val now = TimerAnchor(Duration.ofSeconds(1_000), Instant.parse("2026-10-26T10:00:00Z"))

    @Test fun bandsAreRingingMissedRunningPausedIdle() {
        val rows = timerRows(
            listOf(
                timer(1, state = TimerRunState.Idle),
                timer(2, state = TimerRunState.Paused(Duration.ofSeconds(30))),
                timer(3, state = running(Duration.ofSeconds(30))),
                timer(4, state = TimerRunState.Ringing),
                timer(5, state = TimerRunState.Missed),
            ),
            now,
        )

        assertThat(rows.map { it.id.value }).containsExactly(4L, 5L, 3L, 2L, 1L).inOrder()
        assertThat(rows.map { it.tag })
            .containsExactly(TimerTag.RINGING, TimerTag.MISSED, TimerTag.RUNNING, TimerTag.PAUSED, TimerTag.IDLE)
            .inOrder()
    }

    @Test fun runningAndPausedSortByRemainingAscendingTiesById() {
        val rows = timerRows(
            listOf(
                timer(1, state = running(Duration.ofSeconds(30))),
                timer(2, state = running(Duration.ofSeconds(10))),
                timer(3, state = TimerRunState.Paused(Duration.ofSeconds(20))),
                timer(4, state = TimerRunState.Paused(Duration.ofSeconds(20))),
            ),
            now,
        )

        assertThat(rows.map { it.id.value }).containsExactly(2L, 1L, 3L, 4L).inOrder()
    }

    @Test fun idleIsNewestFirstTiesByLargerId() {
        val earlier = now.wallClock
        val later = earlier.plusSeconds(60)
        val rows = timerRows(
            listOf(
                timer(1, createdAt = earlier, state = TimerRunState.Idle),
                timer(2, createdAt = later, state = TimerRunState.Idle),
                timer(3, createdAt = later, state = TimerRunState.Idle),
            ),
            now,
        )

        assertThat(rows.map { it.id.value }).containsExactly(3L, 2L, 1L).inOrder()
    }

    @Test fun ringingAndMissedOrderById() {
        val rows = timerRows(
            listOf(
                timer(9, state = TimerRunState.Ringing),
                timer(2, state = TimerRunState.Ringing),
                timer(7, state = TimerRunState.Missed),
                timer(3, state = TimerRunState.Missed),
            ),
            now,
        )

        assertThat(rows.map { it.id.value }).containsExactly(2L, 9L, 3L, 7L).inOrder()
    }

    @Test fun runningRemainingRoundsUpToWholeSeconds() {
        val rows = timerRows(
            listOf(
                timer(1, state = running(Duration.ofMillis(4_200))),
                timer(2, state = running(Duration.ofSeconds(4))),
                timer(3, state = running(Duration.ZERO)),
            ),
            now,
        )

        assertThat(rows.associate { it.id.value to it.remaining })
            .containsExactly(
                1L,
                Duration.ofSeconds(5),
                2L,
                Duration.ofSeconds(4),
                3L,
                Duration.ZERO,
            )
    }

    @Test fun idleShowsFullLengthAndRingingAndMissedShowZero() {
        val length = Duration.ofMinutes(5)
        val rows = timerRows(
            listOf(
                timer(1, length = length, state = TimerRunState.Idle),
                timer(2, length = length, state = TimerRunState.Ringing),
                timer(3, length = length, state = TimerRunState.Missed),
            ),
            now,
        )

        assertThat(rows.associate { it.id.value to it.remaining })
            .containsExactly(1L, length, 2L, Duration.ZERO, 3L, Duration.ZERO)
    }

    @Test fun rowCarriesLabelLengthAndEvidence() {
        val row = timerRows(
            listOf(timer(1, length = Duration.ofMinutes(3), state = TimerRunState.Paused(Duration.ofSeconds(90)))),
            now,
        ).single()

        assertThat(row.label).isEqualTo("Tea")
        assertThat(row.length).isEqualTo(Duration.ofMinutes(3))
        assertThat(row.remaining).isEqualTo(Duration.ofSeconds(90))
        assertThat(row.evidenceEnabled).isTrue()
    }

    private fun running(remaining: Duration) = TimerRunState.Running(remaining, now)

    private fun timer(
        id: Long,
        length: Duration = Duration.ofMinutes(5),
        createdAt: Instant = now.wallClock,
        state: TimerRunState,
    ): Timer = Timer.restore(TimerId(id), length, "Tea", null, true, state, createdAt)
}
