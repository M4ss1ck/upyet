package dev.upyet.timer.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimerEvidenceTest {
    private val now = TimerAnchor(Duration.ofMinutes(10), Instant.parse("2026-10-26T08:00:00Z"))

    private fun timerIn(state: TimerRunState) = Timer.restore(
        id = TimerId(1),
        length = Duration.ofMinutes(5),
        label = "Tea",
        ringtoneUri = null,
        evidenceEnabled = false,
        runState = state,
        createdAt = now.wallClock,
    )

    @Test
    fun newTimerTakesTheEvidenceValueItIsCreatedWith() {
        val on = Timer.create(TimerId(1), Duration.ofMinutes(5), "", null, evidenceEnabled = true, createdAt = now.wallClock)
        val off = Timer.create(TimerId(2), Duration.ofMinutes(5), "", null, evidenceEnabled = false, createdAt = now.wallClock)

        assertThat(on.evidenceEnabled).isTrue()
        assertThat(off.evidenceEnabled).isFalse()
        assertThat(on.runState).isEqualTo(TimerRunState.Idle)
    }

    @Test
    fun evidenceIsEditableWhileIdleRunningOrPaused() {
        val states = listOf(
            TimerRunState.Idle,
            TimerRunState.Running(Duration.ofMinutes(3), now),
            TimerRunState.Paused(Duration.ofMinutes(3)),
        )
        for (state in states) {
            val result = timerIn(state).withEvidence(true)

            assertThat(result).isInstanceOf(TimerTransition.Applied::class.java)
            val edited = (result as TimerTransition.Applied).timer
            assertThat(edited.evidenceEnabled).isTrue()
            assertThat(edited.runState).isEqualTo(state)
        }
    }

    @Test
    fun evidenceIsRejectedWhileRinging() {
        val ringing = timerIn(TimerRunState.Ringing)

        assertThat(ringing.withEvidence(true)).isEqualTo(TimerTransition.NotOffered)
        assertThat(ringing.withEvidence(false)).isEqualTo(TimerTransition.NotOffered)
    }

    @Test
    fun evidenceIsRejectedWhileMissed() {
        val missed = timerIn(TimerRunState.Missed)

        assertThat(missed.withEvidence(true)).isEqualTo(TimerTransition.NotOffered)
        assertThat(missed.withEvidence(false)).isEqualTo(TimerTransition.NotOffered)
    }

    @Test
    fun settingEvidenceToItsCurrentValueChangesNothing() {
        assertThat(timerIn(TimerRunState.Idle).withEvidence(false)).isEqualTo(TimerTransition.NoOp)
    }
}
