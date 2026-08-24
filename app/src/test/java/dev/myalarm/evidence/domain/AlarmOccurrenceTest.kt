package dev.myalarm.evidence.domain

import com.google.common.truth.Truth.assertThat
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.OccurrenceId
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
}
