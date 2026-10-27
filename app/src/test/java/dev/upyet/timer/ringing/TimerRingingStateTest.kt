package dev.upyet.timer.ringing

import com.google.common.truth.Truth.assertThat
import dev.upyet.evidence.camera.EvidenceRequest
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.playback.RingingTimer
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimerRingingStateTest {
    private val now = Instant.parse("2026-10-26T12:00:00Z")

    @Test fun nothingSeenYetIsLoadingNotEnded() {
        val state = timerRingingState(emptyList(), now, selected = null, sessionSeen = false)

        assertThat(state.phase).isEqualTo(TimerRingingPhase.LOADING)
        assertThat(state.focused).isNull()
        assertThat(state.evidenceRequest).isNull()
    }

    @Test fun anEmptyShowingAfterOneWasSeenHasEnded() {
        val state = timerRingingState(emptyList(), now, selected = null, sessionSeen = true)

        assertThat(state.phase).isEqualTo(TimerRingingPhase.ENDED)
        assertThat(state.evidenceRequest).isNull()
    }

    @Test fun oneRingingTimerIsInFocusWithItsLengthAndOvertimeAndNoStopAll() {
        val pasta = ringing(1, "Pasta", length = Duration.ofMinutes(9), since = now.minusSeconds(56))

        val state = timerRingingState(listOf(pasta), now, selected = null, sessionSeen = true)

        assertThat(state.phase).isEqualTo(TimerRingingPhase.RINGING)
        assertThat(state.focused).isEqualTo(RingingTimerItem(TimerId(1), "Pasta", Duration.ofMinutes(9), Duration.ofSeconds(56)))
        assertThat(state.queue).isEmpty()
        assertThat(state.showStopAll).isFalse()
    }

    @Test fun focusDefaultsToTheTimerThatStartedRingingMostRecently() {
        val older = ringing(1, "Eggs", since = now.minusSeconds(90))
        val newer = ringing(2, "Pasta", since = now.minusSeconds(10))

        val state = timerRingingState(listOf(older, newer), now, selected = null, sessionSeen = true)

        assertThat(state.focused?.id).isEqualTo(TimerId(2))
        assertThat(state.queue.map { it.id }).containsExactly(TimerId(1))
        assertThat(state.showStopAll).isTrue()
    }

    @Test fun aChosenTimerKeepsTheFocusWhileItRings() {
        val timers = listOf(ringing(1, "Eggs"), ringing(2, "Pasta"), ringing(3, "Tea"))

        val state = timerRingingState(timers, now, selected = TimerId(1), sessionSeen = true)

        assertThat(state.focused?.id).isEqualTo(TimerId(1))
        // The queue keeps the ringing order, so a chip does not jump around when the focus moves.
        assertThat(state.queue.map { it.id }).containsExactly(TimerId(2), TimerId(3)).inOrder()
    }

    @Test fun aChosenTimerThatLeftTheShowingHandsTheFocusBackToTheMostRecent() {
        val timers = listOf(ringing(1, "Eggs"), ringing(2, "Pasta"))

        val state = timerRingingState(timers, now, selected = TimerId(7), sessionSeen = true)

        assertThat(state.focused?.id).isEqualTo(TimerId(2))
    }

    @Test fun overtimeNeverGoesNegativeWhenTheClockIsBehindTheRingStart() {
        val ahead = ringing(1, "Eggs", since = now.plusSeconds(5))

        val state = timerRingingState(listOf(ahead), now, selected = null, sessionSeen = true)

        assertThat(state.focused?.overtime).isEqualTo(Duration.ZERO)
    }

    @Test fun anyRecordingOnTimerTurnsRecordingOnForTheWholeShowing() {
        val timers = listOf(ringing(1, "Eggs", evidence = false), ringing(2, "Pasta", evidence = true))

        val state = timerRingingState(timers, now, selected = TimerId(1), sessionSeen = true)

        assertThat(state.recordingOn).isTrue()
        // The occurrence id is slice #40's: until then nothing owns the recording, so it has no occurrence.
        assertThat(state.evidenceRequest).isEqualTo(EvidenceRequest(occurrenceId = null, evidenceEnabled = true))
    }

    /** Never touching the recorder is what keeps a recording-off showing from releasing an alarm's camera. */
    @Test fun aShowingWithEveryTimerRecordingOffNeverAsksTheRecorderForAnything() {
        val timers = listOf(ringing(1, "Eggs", evidence = false), ringing(2, "Pasta", evidence = false))

        val state = timerRingingState(timers, now, selected = null, sessionSeen = true)

        assertThat(state.recordingOn).isFalse()
        assertThat(state.evidenceRequest).isNull()
    }

    @Test fun anUnreadableTimerStillRingsWithNoLength() {
        val unreadable = ringing(1, "", length = null)

        val state = timerRingingState(listOf(unreadable), now, selected = null, sessionSeen = true)

        assertThat(state.focused?.length).isNull()
        assertThat(state.focused?.label).isEmpty()
    }

    @Test fun clockPartsSplitADurationIntoHoursMinutesAndSeconds() {
        assertThat(ClockParts.of(Duration.ofSeconds(56))).isEqualTo(ClockParts(0, 0, 56))
        assertThat(ClockParts.of(Duration.ofSeconds(9 * 60 + 5))).isEqualTo(ClockParts(0, 9, 5))
        assertThat(ClockParts.of(Duration.ofSeconds(3_600 + 2 * 60 + 3))).isEqualTo(ClockParts(1, 2, 3))
        // Whole seconds only: a counter that showed 0:00 for the first half second would look stuck.
        assertThat(ClockParts.of(Duration.ofMillis(1_999))).isEqualTo(ClockParts(0, 0, 1))
        assertThat(ClockParts.of(Duration.ofSeconds(-4))).isEqualTo(ClockParts(0, 0, 0))
    }

    private fun ringing(
        id: Long,
        label: String,
        length: Duration? = Duration.ofMinutes(5),
        since: Instant = now.minusSeconds(100 - id),
        evidence: Boolean = false,
    ) = RingingTimer(TimerId(id), label, length, ringtoneUri = null, evidenceEnabled = evidence, ringingSince = since)
}
