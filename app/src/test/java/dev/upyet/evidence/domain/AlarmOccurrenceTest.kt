package dev.upyet.evidence.domain

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.timer.domain.TimerId
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class AlarmOccurrenceTest {
    private val ringing = AlarmOccurrence(OccurrenceId(1), AlarmId(2), Instant.EPOCH, null, null, null, OccurrenceOutcome.RINGING, null)

    @Test fun ringingTransitions() {
        assertThat(ringing.withOutcome(OccurrenceOutcome.DISMISSED).outcome).isEqualTo(OccurrenceOutcome.DISMISSED)
        assertThat(ringing.withOutcome(OccurrenceOutcome.SNOOZED).outcome).isEqualTo(OccurrenceOutcome.SNOOZED)
        assertThat(ringing.withOutcome(OccurrenceOutcome.TIMED_OUT).outcome).isEqualTo(OccurrenceOutcome.TIMED_OUT)
    }

    @Test fun snoozeChildKeepsParent() {
        assertThat(ringing.snoozedChild(OccurrenceId(3), Instant.ofEpochSecond(9)).parentOccurrenceId).isEqualTo(ringing.id)
    }

    private val timer = AlarmOccurrence(
        id = OccurrenceId(5),
        alarmId = null,
        scheduledFor = Instant.EPOCH,
        triggeredAt = Instant.EPOCH,
        activityVisibleAt = null,
        dismissedAt = null,
        outcome = OccurrenceOutcome.RINGING,
        parentOccurrenceId = null,
        source = OccurrenceSource.TIMER,
        timers = listOf(TimerAttribution(TimerId(1), "Pasta")),
    )

    @Test fun aTimerStopsWithItsOwnOutcomeAndTimestamp() {
        val stopped = timer.withOutcome(OccurrenceOutcome.STOPPED, Instant.ofEpochSecond(42))

        assertThat(stopped.outcome).isEqualTo(OccurrenceOutcome.STOPPED)
        assertThat(stopped.dismissedAt).isEqualTo(Instant.ofEpochSecond(42))
    }

    @Test fun aTimerNeverDismissesSnoozesOrTimesOut() {
        assertThrows(IllegalStateException::class.java) { timer.withOutcome(OccurrenceOutcome.DISMISSED) }
        assertThrows(IllegalStateException::class.java) { timer.withOutcome(OccurrenceOutcome.SNOOZED) }
        assertThrows(IllegalStateException::class.java) { timer.withOutcome(OccurrenceOutcome.TIMED_OUT) }
    }

    @Test fun anAlarmNeverStops() {
        assertThrows(IllegalStateException::class.java) { ringing.withOutcome(OccurrenceOutcome.STOPPED) }
    }

    @Test fun aTimerNeverSnoozes() {
        assertThrows(IllegalStateException::class.java) { timer.snoozedChild(OccurrenceId(6), Instant.EPOCH) }
    }

    @Test fun anAlarmOccurrenceNeedsAnAlarmId() {
        assertThrows(IllegalArgumentException::class.java) {
            AlarmOccurrence(OccurrenceId(1), null, Instant.EPOCH, null, null, null, OccurrenceOutcome.RINGING, null)
        }
    }

    @Test fun aTimerOccurrenceHasNoAlarmId() {
        assertThrows(IllegalArgumentException::class.java) {
            timer.copy(alarmId = AlarmId(2))
        }
    }

    @Test fun aTimerOccurrenceHasNoParent() {
        assertThrows(IllegalArgumentException::class.java) {
            timer.copy(parentOccurrenceId = OccurrenceId(1))
        }
    }
}
