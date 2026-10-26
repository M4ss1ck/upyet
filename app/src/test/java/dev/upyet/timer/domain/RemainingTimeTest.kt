package dev.upyet.timer.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RemainingTimeTest {
    private val anchor = TimerAnchor(elapsedRealtime = Duration.ofMinutes(10), wallClock = Instant.parse("2026-10-26T08:00:00Z"))
    private val fiveMinutes = Duration.ofMinutes(5)

    private fun later(elapsed: Duration, wall: Duration) = TimerAnchor(
        elapsedRealtime = anchor.elapsedRealtime.plus(elapsed),
        wallClock = anchor.wallClock.plus(wall),
    )

    @Test
    fun elapsedPairCountsDownByTheElapsedDelta() {
        val now = later(elapsed = Duration.ofSeconds(90), wall = Duration.ofHours(3))
        assertThat(RemainingTime.fromElapsed(fiveMinutes, anchor, now)).isEqualTo(Duration.ofSeconds(210))
    }

    @Test
    fun wallClockPairCountsDownByTheWallClockDelta() {
        val now = later(elapsed = Duration.ofHours(3), wall = Duration.ofSeconds(90))
        assertThat(RemainingTime.fromWallClock(fiveMinutes, anchor, now)).isEqualTo(Duration.ofSeconds(210))
    }

    @Test
    fun bothPairsClampAtZeroOncePastTheEnd() {
        val now = later(elapsed = Duration.ofMinutes(7), wall = Duration.ofMinutes(7))
        assertThat(RemainingTime.fromElapsed(fiveMinutes, anchor, now)).isEqualTo(Duration.ZERO)
        assertThat(RemainingTime.fromWallClock(fiveMinutes, anchor, now)).isEqualTo(Duration.ZERO)
        assertThat(RemainingTime.of(fiveMinutes, anchor, now)).isEqualTo(Duration.ZERO)
    }

    @Test
    fun exactlyAtTheEndIsZero() {
        val now = later(elapsed = fiveMinutes, wall = fiveMinutes)
        assertThat(RemainingTime.of(fiveMinutes, anchor, now)).isEqualTo(Duration.ZERO)
    }

    @Test
    fun ofPrefersTheElapsedPairWhenItsDeltaIsNotNegative() {
        // The user moved the wall clock forward an hour; elapsed time is the truth.
        val now = later(elapsed = Duration.ofMinutes(1), wall = Duration.ofMinutes(61))
        assertThat(RemainingTime.of(fiveMinutes, anchor, now)).isEqualTo(Duration.ofMinutes(4))
    }

    @Test
    fun negativeElapsedDeltaFallsBackToTheWallClockAnswer() {
        // Rebooted before the anchor was rewritten: elapsedRealtime restarted near zero.
        val now = TimerAnchor(elapsedRealtime = Duration.ofSeconds(20), wallClock = anchor.wallClock.plusSeconds(120))
        assertThat(RemainingTime.of(fiveMinutes, anchor, now)).isEqualTo(Duration.ofMinutes(3))
    }

    @Test
    fun negativeElapsedDeltaPastTheEndIsZeroNotNegative() {
        val now = TimerAnchor(elapsedRealtime = Duration.ofSeconds(20), wallClock = anchor.wallClock.plusSeconds(600))
        assertThat(RemainingTime.of(fiveMinutes, anchor, now)).isEqualTo(Duration.ZERO)
    }

    @Test
    fun bothDeltasNegativeNeverExceedsRemainingAtAnchor() {
        // Clock and elapsed both behind the anchor: no time has provably passed, none is invented.
        val now = TimerAnchor(elapsedRealtime = Duration.ofSeconds(20), wallClock = anchor.wallClock.minusSeconds(600))
        assertThat(RemainingTime.of(fiveMinutes, anchor, now)).isEqualTo(fiveMinutes)
        assertThat(RemainingTime.fromWallClock(fiveMinutes, anchor, now)).isEqualTo(fiveMinutes)
    }
}
