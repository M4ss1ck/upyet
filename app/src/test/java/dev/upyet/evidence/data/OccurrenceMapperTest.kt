package dev.upyet.evidence.data

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.core.database.AlarmOccurrenceEntity
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceSource
import dev.upyet.evidence.domain.TimerAttribution
import dev.upyet.timer.domain.TimerId
import org.junit.Test
import java.time.Instant

class OccurrenceMapperTest {
    @Test
    fun aLegacyRowWithoutASourceReadsBackAsAnAlarm() {
        val legacy = AlarmOccurrenceEntity(
            id = 4,
            alarmId = 3,
            scheduledFor = 1_000,
            triggeredAt = 1_001,
            activityVisibleAt = null,
            dismissedAt = 1_003,
            outcome = "DISMISSED",
            parentOccurrenceId = null,
        )

        val occurrence = legacy.toDomain()

        assertThat(occurrence.source).isEqualTo(OccurrenceSource.ALARM)
        assertThat(occurrence.alarmId).isEqualTo(AlarmId(3))
        assertThat(occurrence.timers).isEmpty()
        assertThat(occurrence.outcome).isEqualTo(OccurrenceOutcome.DISMISSED)
    }

    @Test
    fun anUnknownSourceReadsBackAsAnAlarm() {
        val entity =
            AlarmOccurrenceEntity(1, 3, 1_000, null, null, null, "RINGING", null, source = "SOMETHING_NEW", timerAttribution = "1:3:Tea")

        val occurrence = entity.toDomain()

        assertThat(occurrence.source).isEqualTo(OccurrenceSource.ALARM)
        assertThat(occurrence.alarmId).isEqualTo(AlarmId(3))
        assertThat(occurrence.timers).isEmpty()
    }

    @Test
    fun aTimerOccurrenceRoundTripsWithItsAttribution() {
        val timer = AlarmOccurrence(
            id = OccurrenceId(9),
            alarmId = null,
            scheduledFor = Instant.ofEpochMilli(5_000),
            triggeredAt = Instant.ofEpochMilli(5_001),
            activityVisibleAt = Instant.ofEpochMilli(5_002),
            dismissedAt = Instant.ofEpochMilli(5_600),
            outcome = OccurrenceOutcome.STOPPED,
            parentOccurrenceId = null,
            source = OccurrenceSource.TIMER,
            timers = listOf(TimerAttribution(TimerId(1), "Pasta: al dente"), TimerAttribution(TimerId(2), "🫖 tea")),
        )

        val entity = timer.toEntity()
        val back = entity.toDomain()

        assertThat(entity.source).isEqualTo("TIMER")
        assertThat(back).isEqualTo(timer)
    }

    @Test
    fun aTimerEntityMapsItsAlarmIdToNull() {
        val entity = AlarmOccurrenceEntity(
            id = 2,
            alarmId = AlarmOccurrenceEntity.NO_ALARM_ID,
            scheduledFor = 1_000,
            triggeredAt = 1_000,
            activityVisibleAt = null,
            dismissedAt = null,
            outcome = "RINGING",
            parentOccurrenceId = null,
            source = "TIMER",
            timerAttribution = "1:3:Tea",
        )

        val occurrence = entity.toDomain()

        assertThat(occurrence.alarmId).isNull()
        assertThat(occurrence.source).isEqualTo(OccurrenceSource.TIMER)
        assertThat(occurrence.timers).containsExactly(TimerAttribution(TimerId(1), "Tea"))
    }

    @Test
    fun anAlarmOccurrenceWritesNoAttribution() {
        val alarm = AlarmOccurrence(
            OccurrenceId(1),
            AlarmId(3),
            Instant.ofEpochMilli(1_000),
            null,
            null,
            null,
            OccurrenceOutcome.RINGING,
            null,
        )

        val entity = alarm.toEntity()

        assertThat(entity.alarmId).isEqualTo(3L)
        assertThat(entity.source).isEqualTo("ALARM")
        assertThat(entity.timerAttribution).isNull()
    }
}
