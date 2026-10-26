package dev.upyet.timer.playback

import com.google.common.truth.Truth.assertThat
import dev.upyet.timer.domain.TimerId
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Issue #14, heads-up variant B: actions only while no ringing timer records (ADR 0010, AGENTS.md §7). */
class TimerHeadsUpTest {
    private fun ringing(id: Long, evidence: Boolean = false) =
        RingingTimer(TimerId(id), "Tea", Duration.ofMinutes(5), null, evidence, Instant.EPOCH)

    @Test
    fun oneTimerWithoutEvidenceOffersStopAndAMinuteForThatTimer() {
        assertThat(TimerHeadsUp.of(listOf(ringing(7)))).isEqualTo(TimerHeadsUp.StopOrAddMinute(TimerId(7)))
    }

    @Test
    fun severalTimersWithoutEvidenceOfferStopAll() {
        assertThat(TimerHeadsUp.of(listOf(ringing(1), ringing(2)))).isEqualTo(TimerHeadsUp.StopAll)
    }

    @Test
    fun oneRecordingTimerLeavesOnlyTapToOpen() {
        assertThat(TimerHeadsUp.of(listOf(ringing(1, evidence = true)))).isEqualTo(TimerHeadsUp.TapToOpen)
    }

    @Test
    fun anyRecordingTimerAmongSeveralLeavesOnlyTapToOpen() {
        assertThat(TimerHeadsUp.of(listOf(ringing(1), ringing(2, evidence = true), ringing(3)))).isEqualTo(TimerHeadsUp.TapToOpen)
    }

    @Test
    fun aTimerWhoseDetailsCouldNotBeReadLeavesOnlyTapToOpen() {
        val unreadable = ringing(1).copy(detailsUnreadable = true)

        assertThat(TimerHeadsUp.of(listOf(unreadable))).isEqualTo(TimerHeadsUp.TapToOpen)
    }

    @Test
    fun nothingRingingOffersNothingThatEndsARing() {
        assertThat(TimerHeadsUp.of(emptyList())).isEqualTo(TimerHeadsUp.TapToOpen)
    }
}
