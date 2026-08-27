package dev.upyet.alarm.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class UpcomingAlarmSelectorTest {
    private val t1 = Instant.parse("2026-08-24T10:00:00Z")
    private val t2 = Instant.parse("2026-08-24T11:00:00Z")
    private val t3 = Instant.parse("2026-08-24T12:00:00Z")

    @Test
    fun earliestOfSeveralCandidatesWins() {
        val early = alarm(1, Recurrence.Daily) to t2
        val later = alarm(2, Recurrence.Daily) to t3
        val earliest = alarm(3, Recurrence.Daily) to t1

        val result = UpcomingAlarmSelector.select(listOf(later, early, earliest), leadMinutes = 60)

        assertThat(result?.alarmId).isEqualTo(AlarmId(3))
        assertThat(result?.triggerAt).isEqualTo(t1)
    }

    @Test
    fun leadMinutesZeroReturnsNull() {
        val candidate = alarm(1, Recurrence.Daily) to t1

        assertThat(UpcomingAlarmSelector.select(listOf(candidate), leadMinutes = 0)).isNull()
    }

    @Test
    fun emptyCandidatesReturnsNull() {
        assertThat(UpcomingAlarmSelector.select(emptyList(), leadMinutes = 60)).isNull()
    }

    @Test
    fun postAtIsExactlyTriggerMinusLead() {
        val candidate = alarm(1, Recurrence.Daily) to t2

        val result = UpcomingAlarmSelector.select(listOf(candidate), leadMinutes = 60)

        assertThat(result?.postAt).isEqualTo(t2.minusSeconds(60L * 60L))
    }

    @Test
    fun canSkipFalseForOneTime() {
        val candidate = alarm(1, Recurrence.OneTime) to t1

        val result = UpcomingAlarmSelector.select(listOf(candidate), leadMinutes = 60)

        assertThat(result?.canSkip).isFalse()
    }

    @Test
    fun canSkipTrueForDaily() {
        val candidate = alarm(1, Recurrence.Daily) to t1

        val result = UpcomingAlarmSelector.select(listOf(candidate), leadMinutes = 60)

        assertThat(result?.canSkip).isTrue()
    }

    @Test
    fun canSkipTrueForWeekly() {
        val candidate = alarm(1, Recurrence.Weekly(setOf(DayOfWeek.MONDAY))) to t1

        val result = UpcomingAlarmSelector.select(listOf(candidate), leadMinutes = 60)

        assertThat(result?.canSkip).isTrue()
    }

    private fun alarm(id: Long, recurrence: Recurrence) = Alarm(
        AlarmId(id),
        LocalTime.of(7, 0),
        true,
        "label",
        recurrence,
        null,
        true,
        9,
        false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
