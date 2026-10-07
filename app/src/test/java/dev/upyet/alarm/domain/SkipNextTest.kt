package dev.upyet.alarm.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SkipNextTest {
    private val utc = ZoneId.of("UTC")
    private val newYork = ZoneId.of("America/New_York")
    private val monday = LocalDate.of(2026, 8, 24)

    @Test
    fun noStoredSkipIsNeverActive() {
        assertThat(SkipNext.activeDate(daily(LocalTime.of(7, 0)), utc, instant("2026-08-24T06:00:00Z"))).isNull()
    }

    @Test
    fun skipIsActiveUntilTheMinuteBeforeTheSuppressedOccurrence() {
        val alarm = daily(LocalTime.of(7, 0), skipNextOn = monday)

        assertThat(SkipNext.activeDate(alarm, utc, instant("2026-08-24T06:59:00Z"))).isEqualTo(monday)
    }

    @Test
    fun skipLapsesExactlyWhenTheSuppressedOccurrenceWouldHaveRung() {
        val alarm = daily(LocalTime.of(7, 0), skipNextOn = monday)

        assertThat(SkipNext.activeDate(alarm, utc, instant("2026-08-24T07:00:00Z"))).isNull()
    }

    /** The reported bug: the badge outlived the alarm time and stayed until midnight. */
    @Test
    fun skipHasLapsedLaterTheSameDay() {
        val alarm = daily(LocalTime.of(7, 0), skipNextOn = monday)

        assertThat(SkipNext.activeDate(alarm, utc, instant("2026-08-24T07:01:00Z"))).isNull()
        assertThat(SkipNext.activeDate(alarm, utc, instant("2026-08-24T23:59:00Z"))).isNull()
    }

    @Test
    fun skipForALaterDayStaysActive() {
        val alarm = daily(LocalTime.of(7, 0), skipNextOn = monday.plusDays(1))

        assertThat(SkipNext.activeDate(alarm, utc, instant("2026-08-24T10:00:00Z"))).isEqualTo(monday.plusDays(1))
    }

    /** 02:30 does not exist on 2026-03-08 in New York; the calculator rings at 03:30 EDT, so the skip lapses then. */
    @Test
    fun springForwardGapLapsesWhereTheCalculatorWouldHaveRung() {
        val gapDay = LocalDate.of(2026, 3, 8)
        val alarm = daily(LocalTime.of(2, 30), skipNextOn = gapDay)
        val wouldHaveRung = NextOccurrenceCalculator.next(alarm.time, alarm.recurrence, newYork, instant("2026-03-08T05:00:00Z"))!!

        assertThat(wouldHaveRung).isEqualTo(instant("2026-03-08T07:30:00Z"))
        assertThat(SkipNext.activeDate(alarm, newYork, wouldHaveRung.minusSeconds(60))).isEqualTo(gapDay)
        assertThat(SkipNext.activeDate(alarm, newYork, wouldHaveRung)).isNull()
    }

    /** 01:30 happens twice on 2026-11-01 in New York; the earlier (EDT) one is the occurrence, and the skip lapses there. */
    @Test
    fun fallBackOverlapLapsesAtTheEarlierOffset() {
        val overlapDay = LocalDate.of(2026, 11, 1)
        val alarm = daily(LocalTime.of(1, 30), skipNextOn = overlapDay)

        assertThat(SkipNext.activeDate(alarm, newYork, instant("2026-11-01T05:29:00Z"))).isEqualTo(overlapDay)
        assertThat(SkipNext.activeDate(alarm, newYork, instant("2026-11-01T05:30:00Z"))).isNull()
    }

    /** Local-time semantics: 07:00 means 07:00 where the user is now, so a zone change moves the lapse with it. */
    @Test
    fun timezoneChangeMovesTheLapse() {
        val alarm = daily(LocalTime.of(7, 0), skipNextOn = monday)
        val now = instant("2026-08-24T10:00:00Z")

        assertThat(SkipNext.activeDate(alarm, utc, now)).isNull()
        assertThat(SkipNext.activeDate(alarm, ZoneId.of("America/Los_Angeles"), now)).isEqualTo(monday)
    }

    @Test
    fun targetIsTheNextOccurrenceDate() {
        assertThat(SkipNext.target(daily(LocalTime.of(11, 0)), utc, instant("2026-08-24T10:00:00Z"))).isEqualTo(monday)
        assertThat(SkipNext.target(daily(LocalTime.of(7, 0)), utc, instant("2026-08-24T10:00:00Z"))).isEqualTo(monday.plusDays(1))
    }

    /** Re-skipping after a lapse targets the following occurrence, never the one that already passed. */
    @Test
    fun targetAfterALapsedSkipIsTheFollowingOccurrence() {
        val alarm = daily(LocalTime.of(7, 0), skipNextOn = monday)

        assertThat(SkipNext.target(alarm, utc, instant("2026-08-24T10:00:00Z"))).isEqualTo(monday.plusDays(1))
    }

    @Test
    fun weeklyTargetSkipsToTheNextAllowedDay() {
        val alarm = daily(LocalTime.of(7, 0)).copy(recurrence = Recurrence.Weekly(setOf(DayOfWeek.WEDNESDAY)))

        assertThat(SkipNext.target(alarm, utc, instant("2026-08-24T10:00:00Z"))).isEqualTo(LocalDate.of(2026, 8, 26))
    }

    @Test
    fun oneTimeAndDisabledAlarmsCannotSkip() {
        val now = instant("2026-08-24T10:00:00Z")
        val oneTime = daily(LocalTime.of(11, 0)).copy(recurrence = Recurrence.OneTime)
        val disabled = daily(LocalTime.of(11, 0)).copy(enabled = false)

        assertThat(SkipNext.canSkip(oneTime)).isFalse()
        assertThat(SkipNext.canSkip(disabled)).isFalse()
        assertThat(SkipNext.target(oneTime, utc, now)).isNull()
        assertThat(SkipNext.target(disabled, utc, now)).isNull()
    }

    private fun instant(text: String) = Instant.parse(text)

    private fun daily(time: LocalTime, skipNextOn: LocalDate? = null) = Alarm(
        AlarmId(1),
        time,
        true,
        "label",
        Recurrence.Daily,
        null,
        true,
        true,
        9,
        false,
        skipNextOn = skipNextOn,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
