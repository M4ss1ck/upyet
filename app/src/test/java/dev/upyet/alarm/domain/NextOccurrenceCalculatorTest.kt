package dev.upyet.alarm.domain

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
    fun dailyReturnsTodayWhenTheTimeIsStillAhead() {
        val result = NextOccurrenceCalculator.next(LocalTime.of(7, 30), Recurrence.Daily, zone, instantAt("2027-01-10", "06:00"))

        assertThat(result).isEqualTo(instantAt("2027-01-10", "07:30"))
    }

    /** Without [inclusive] the alarm standing exactly at now has already fired, so the next one is tomorrow. */
    @Test
    fun anExactMatchIsSkippedWhenNotInclusive() {
        val from = instantAt("2027-01-10", "07:30")

        val result = NextOccurrenceCalculator.next(LocalTime.of(7, 30), Recurrence.Daily, zone, from)

        assertThat(result).isEqualTo(instantAt("2027-01-11", "07:30"))
    }

    @Test
    fun midnightRollsToTheNextDayNotBackToTheStartOfToday() {
        val result = NextOccurrenceCalculator.next(LocalTime.MIDNIGHT, Recurrence.Daily, zone, instantAt("2027-01-10", "08:00"))

        assertThat(result).isEqualTo(instantAt("2027-01-11", "00:00"))
    }

    @Test
    fun weeklyPicksTheSoonestOfSeveralDays() {
        // Sunday 10 Jan 2027: Tuesday is nearer than Friday.
        val result = NextOccurrenceCalculator.next(
            LocalTime.of(7, 30),
            Recurrence.Weekly(setOf(DayOfWeek.FRIDAY, DayOfWeek.TUESDAY)),
            zone,
            instantAt("2027-01-10", "08:00"),
        )

        assertThat(result).isEqualTo(instantAt("2027-01-12", "07:30"))
    }

    @Test
    fun weeklyReturnsTodayWhenTodayIsAnAllowedDayAndTheTimeIsAhead() {
        // 10 Jan 2027 is a Sunday.
        val result = NextOccurrenceCalculator.next(
            LocalTime.of(7, 30),
            Recurrence.Weekly(setOf(DayOfWeek.SUNDAY)),
            zone,
            instantAt("2027-01-10", "06:00"),
        )

        assertThat(result).isEqualTo(instantAt("2027-01-10", "07:30"))
    }

    @Test
    fun weeklyOnEverySevenDaysMatchesDaily() {
        val from = instantAt("2027-01-10", "08:00")
        val everyDay = Recurrence.Weekly(DayOfWeek.entries.toSet())

        assertThat(NextOccurrenceCalculator.next(LocalTime.of(7, 30), everyDay, zone, from))
            .isEqualTo(NextOccurrenceCalculator.next(LocalTime.of(7, 30), Recurrence.Daily, zone, from))
    }

    /**
     * The eight-day search window has to cover the worst case: a single weekday whose time has already
     * passed today is a full seven days out. Any narrower window would silently return no occurrence.
     */
    @Test
    fun everySingleWeekdayHasAnOccurrenceFromEveryStartingDay() {
        DayOfWeek.entries.forEach { startDay ->
            val from = ZonedDateTime.of(LocalDate.of(2027, 1, 10), LocalTime.of(8, 0), zone)
                .with(java.time.temporal.TemporalAdjusters.nextOrSame(startDay))
                .toInstant()
            DayOfWeek.entries.forEach { alarmDay ->
                val result = NextOccurrenceCalculator.next(LocalTime.of(7, 30), Recurrence.Weekly(setOf(alarmDay)), zone, from)

                assertThat(result).isNotNull()
                assertThat(result!!.atZone(zone).dayOfWeek).isEqualTo(alarmDay)
                assertThat(result).isGreaterThan(from)
            }
        }
    }

    @Test
    fun everyRecurrenceAlwaysReturnsAnInstantAfterFrom() {
        val from = instantAt("2027-01-10", "07:30")
        val recurrences = listOf(
            Recurrence.OneTime,
            Recurrence.Daily,
            Recurrence.Weekly(setOf(DayOfWeek.SUNDAY)),
            Recurrence.Weekly(DayOfWeek.entries.toSet()),
        )

        recurrences.forEach { recurrence ->
            assertThat(NextOccurrenceCalculator.next(LocalTime.of(7, 30), recurrence, zone, from)).isGreaterThan(from)
        }
    }

    @Test
    fun springForwardGapShiftsADailyAlarmForwardToo() {
        val result = NextOccurrenceCalculator.next(LocalTime.of(2, 30), Recurrence.Daily, zone, instantAt("2027-03-13", "12:00"))

        assertThat(result).isEqualTo(instantAt("2027-03-14", "03:30"))
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

    @Test
    fun skippingTodaysDailyOccurrenceReturnsTomorrow() {
        val from = instantAt("2027-01-10", "06:00")

        val result = NextOccurrenceCalculator.next(
            LocalTime.of(7, 30),
            Recurrence.Daily,
            zone,
            from,
            skipOn = LocalDate.parse("2027-01-10"),
        )

        assertThat(result).isEqualTo(instantAt("2027-01-11", "07:30"))
    }

    @Test
    fun skippingAWeeklyOccurrenceReturnsTheNextAllowedWeekday() {
        // 10 Jan 2027 is a Sunday. Weekly Mon+Wed: without skip the next is Mon 11 Jan; with Mon skipped it is Wed 13 Jan.
        val from = instantAt("2027-01-10", "06:00")

        val result = NextOccurrenceCalculator.next(
            LocalTime.of(7, 30),
            Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            zone,
            from,
            skipOn = LocalDate.parse("2027-01-11"),
        )

        assertThat(result).isEqualTo(instantAt("2027-01-13", "07:30"))
    }

    @Test
    fun skipDateThatIsNotAnOccurrenceDateChangesNothing() {
        val from = instantAt("2027-01-10", "06:00")

        val withoutSkip = NextOccurrenceCalculator.next(
            LocalTime.of(7, 30),
            Recurrence.Weekly(setOf(DayOfWeek.MONDAY)),
            zone,
            from,
        )
        val withIrrelevantSkip = NextOccurrenceCalculator.next(
            LocalTime.of(7, 30),
            Recurrence.Weekly(setOf(DayOfWeek.MONDAY)),
            zone,
            from,
            skipOn = LocalDate.parse("2027-01-12"),
        )

        assertThat(withIrrelevantSkip).isEqualTo(withoutSkip)
        assertThat(withIrrelevantSkip).isEqualTo(instantAt("2027-01-11", "07:30"))
    }

    @Test
    fun staleSkipDateInThePastChangesNothing() {
        val from = instantAt("2027-01-10", "06:00")

        val withoutSkip = NextOccurrenceCalculator.next(LocalTime.of(7, 30), Recurrence.Daily, zone, from)
        val withStaleSkip = NextOccurrenceCalculator.next(
            LocalTime.of(7, 30),
            Recurrence.Daily,
            zone,
            from,
            skipOn = LocalDate.parse("2027-01-09"),
        )

        assertThat(withStaleSkip).isEqualTo(withoutSkip)
        assertThat(withStaleSkip).isEqualTo(instantAt("2027-01-10", "07:30"))
    }

    private fun instantAt(date: String, time: String): Instant = ZonedDateTime
        .of(
            LocalDate.parse(date),
            LocalTime.parse(time),
            zone,
        ).toInstant()
}
