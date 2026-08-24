package dev.myalarm.alarm.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NextOccurrenceCalculatorTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun oneTimeBeforeAlarmReturnsToday() {
        val from = instantAt("2027-01-10", "06:00")

        val result =
            NextOccurrenceCalculator.next(
                LocalTime.of(7, 30),
                Recurrence.OneTime,
                zone,
                from,
            )

        assertThat(
            result,
        ).isEqualTo(instantAt("2027-01-10", "07:30"))
    }

    @Test
    fun oneTimeAfterAlarmReturnsTomorrow() {
        val from = instantAt("2027-01-10", "08:00")

        val result =
            NextOccurrenceCalculator.next(
                LocalTime.of(7, 30),
                Recurrence.OneTime,
                zone,
                from,
            )

        assertThat(
            result,
        ).isEqualTo(instantAt("2027-01-11", "07:30"))
    }

    @Test
    fun dailyReturnsTomorrowWhenTodayHasPassed() {
        val from = instantAt("2027-01-10", "08:00")

        val result =
            NextOccurrenceCalculator.next(
                LocalTime.of(7, 30),
                Recurrence.Daily,
                zone,
                from,
            )

        assertThat(
            result,
        ).isEqualTo(instantAt("2027-01-11", "07:30"))
    }

    @Test
    fun weeklyReturnsNextAllowedDay() {
        val from = instantAt("2027-01-11", "08:00")

        val result =
            NextOccurrenceCalculator.next(
                LocalTime.of(7, 30),
                Recurrence.Weekly(
                    setOf(DayOfWeek.WEDNESDAY),
                ),
                zone,
                from,
            )

        assertThat(
            result,
        ).isEqualTo(instantAt("2027-01-13", "07:30"))
    }

    @Test
    fun weeklyRollsOverToFollowingWeek() {
        val from = instantAt("2027-01-15", "08:00")

        val result =
            NextOccurrenceCalculator.next(
                LocalTime.of(7, 30),
                Recurrence.Weekly(setOf(DayOfWeek.MONDAY)),
                zone,
                from,
            )

        assertThat(
            result,
        ).isEqualTo(instantAt("2027-01-18", "07:30"))
    }

    @Test
    fun weeklySingleDayCanBeSevenDaysOut() {
        val from = instantAt("2027-01-10", "08:00")

        val result =
            NextOccurrenceCalculator.next(
                LocalTime.of(7, 30),
                Recurrence.Weekly(setOf(DayOfWeek.SUNDAY)),
                zone,
                from,
            )

        assertThat(
            result,
        ).isEqualTo(instantAt("2027-01-17", "07:30"))
    }

    @Test
    fun springForwardGapShiftsAlarmForward() {
        val result =
            NextOccurrenceCalculator.next(
                LocalTime.of(2, 30),
                Recurrence.OneTime,
                zone,
                instantAt("2027-03-13", "12:00"),
            )

        assertThat(
            result,
        ).isEqualTo(instantAt("2027-03-14", "03:30"))
    }

    @Test
    fun fallBackOverlapUsesEarlierOffset() {
        val result =
            NextOccurrenceCalculator.next(
                LocalTime.of(1, 30),
                Recurrence.OneTime,
                zone,
                instantAt("2027-11-06", "12:00"),
            )

        assertThat(
            result,
        ).isEqualTo(
            ZonedDateTime
                .of(
                    2027,
                    11,
                    7,
                    1,
                    30,
                    0,
                    0,
                    zone,
                ).withEarlierOffsetAtOverlap()
                .toInstant(),
        )
    }

    @Test
    fun sameWallClockTimeUsesEachZone() {
        val from = Instant.parse("2027-01-10T00:00:00Z")

        val newYork =
            NextOccurrenceCalculator.next(
                LocalTime.NOON,
                Recurrence.OneTime,
                ZoneId.of("America/New_York"),
                from,
            )
        val tokyo =
            NextOccurrenceCalculator.next(
                LocalTime.NOON,
                Recurrence.OneTime,
                ZoneId.of("Asia/Tokyo"),
                from,
            )

        assertThat(
            newYork,
        ).isEqualTo(Instant.parse("2027-01-10T17:00:00Z"))
        assertThat(
            tokyo,
        ).isEqualTo(Instant.parse("2027-01-10T03:00:00Z"))
    }

    @Test
    fun inclusiveAcceptsExactInstant() {
        val from = instantAt("2027-01-10", "07:30")

        val result =
            NextOccurrenceCalculator.next(
                LocalTime.of(7, 30),
                Recurrence.Daily,
                zone,
                from,
                inclusive = true,
            )

        assertThat(result).isEqualTo(from)
    }

    private fun instantAt(date: String, time: String): Instant = ZonedDateTime
        .of(
            LocalDate.parse(date),
            LocalTime.parse(time),
            zone,
        ).toInstant()
}
