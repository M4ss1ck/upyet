package dev.upyet.alarm.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class NextAlarmTest {
    private val zone = ZoneId.of("UTC")
    private val from = Instant.parse("2027-01-10T06:00:00Z")

    @Test
    fun picksTheEnabledAlarmThatFiresSoonest() {
        val soon = alarm(id = 1, time = LocalTime.of(7, 0), enabled = true)
        val later = alarm(id = 2, time = LocalTime.of(9, 0), enabled = true)

        val result = NextAlarm.select(listOf(later, soon), zone, from)

        assertThat(result?.time).isEqualTo(soon.time)
        assertThat(result?.label).isEqualTo(soon.label)
    }

    @Test
    fun ignoresDisabledAlarmsEvenWhenTheyFireSooner() {
        val disabledSoon = alarm(id = 1, time = LocalTime.of(6, 30), enabled = false)
        val enabledLater = alarm(id = 2, time = LocalTime.of(9, 0), enabled = true)

        val result = NextAlarm.select(listOf(disabledSoon, enabledLater), zone, from)

        assertThat(result?.time).isEqualTo(enabledLater.time)
    }

    @Test
    fun returnsNullWhenNoAlarmIsEnabled() {
        val result = NextAlarm.select(listOf(alarm(id = 1, time = LocalTime.of(7, 0), enabled = false)), zone, from)

        assertThat(result).isNull()
    }

    @Test
    fun returnsNullWhenThereAreNoAlarmsAtAll() {
        assertThat(NextAlarm.select(emptyList(), zone, from)).isNull()
    }

    /** A one-time alarm is switched off once it has rung, so retiring it must clear the hero card. */
    @Test
    fun aRetiredOneTimeAlarmIsNeverTheNextAlarm() {
        val rung = alarm(id = 1, time = LocalTime.of(5, 0), enabled = false)

        assertThat(NextAlarm.select(listOf(rung), zone, from)).isNull()
    }

    /** Whatever it picks must still be ahead of now - a hero card counting down to the past is the bug. */
    @Test
    fun theChosenAlarmAlwaysFiresAfterNow() {
        val alarms = listOf(
            alarm(id = 1, time = LocalTime.of(5, 0), enabled = true),
            alarm(id = 2, time = LocalTime.of(6, 0), enabled = true),
            alarm(id = 3, time = LocalTime.MIDNIGHT, enabled = true),
        )

        val result = NextAlarm.select(alarms, zone, from)

        assertThat(result!!.firesAt).isGreaterThan(from)
    }

    /** An alarm whose time has passed today rolls to tomorrow, so a later time today still wins. */
    @Test
    fun aTimeStillAheadTodayBeatsOneThatHasToWaitForTomorrow() {
        val passedToday = alarm(id = 1, time = LocalTime.of(5, 0), enabled = true)
        val laterToday = alarm(id = 2, time = LocalTime.of(23, 0), enabled = true)

        val result = NextAlarm.select(listOf(passedToday, laterToday), zone, from)

        assertThat(result?.time).isEqualTo(laterToday.time)
    }

    @Test
    fun comparesAcrossRecurrenceKinds() {
        // 10 Jan 2027 is a Sunday, so the Monday alarm is a day out while the daily one is later today.
        val weeklyMonday = alarm(id = 1, time = LocalTime.of(7, 0), enabled = true)
            .copy(recurrence = Recurrence.Weekly(setOf(DayOfWeek.MONDAY)))
        val dailyTonight = alarm(id = 2, time = LocalTime.of(22, 0), enabled = true).copy(recurrence = Recurrence.Daily)

        val result = NextAlarm.select(listOf(weeklyMonday, dailyTonight), zone, from)

        assertThat(result?.time).isEqualTo(dailyTonight.time)
    }

    /** Two alarms at the same instant: the first in the list wins, so the hero does not flicker between them. */
    @Test
    fun aTieKeepsTheFirstAlarmInTheList() {
        val first = alarm(id = 1, time = LocalTime.of(7, 0), enabled = true)
        val second = alarm(id = 2, time = LocalTime.of(7, 0), enabled = true)

        assertThat(NextAlarm.select(listOf(first, second), zone, from)?.label).isEqualTo(first.label)
        assertThat(NextAlarm.select(listOf(second, first), zone, from)?.label).isEqualTo(second.label)
    }

    @Test
    fun carriesTheAlarmsOwnLabelTimeAndEvidenceFlag() {
        val withEvidence = alarm(id = 1, time = LocalTime.of(7, 0), enabled = true).copy(evidenceEnabled = true)

        val result = NextAlarm.select(listOf(withEvidence), zone, from)

        assertThat(result?.time).isEqualTo(LocalTime.of(7, 0))
        assertThat(result?.label).isEqualTo("Alarm 1")
        assertThat(result?.evidenceEnabled).isTrue()
        assertThat(result?.firesAt).isEqualTo(Instant.parse("2027-01-10T07:00:00Z"))
    }

    /** The hero reads the alarm in the device's zone, not UTC, so the same alarm fires at a different instant. */
    @Test
    fun theChosenInstantFollowsTheZone() {
        val morning = alarm(id = 1, time = LocalTime.of(7, 0), enabled = true)
        val midnightUtc = Instant.parse("2027-01-10T00:00:00Z")

        val tokyo = NextAlarm.select(listOf(morning), ZoneId.of("Asia/Tokyo"), midnightUtc)
        val newYork = NextAlarm.select(listOf(morning), ZoneId.of("America/New_York"), midnightUtc)

        // Tokyo is already at 09:00, so its 07:00 alarm waits for tomorrow; New York is still on the 9th.
        assertThat(tokyo?.firesAt).isEqualTo(Instant.parse("2027-01-10T22:00:00Z"))
        assertThat(newYork?.firesAt).isEqualTo(Instant.parse("2027-01-10T12:00:00Z"))
    }

    private fun alarm(id: Long, time: LocalTime, enabled: Boolean) = Alarm(
        AlarmId(id), time, enabled, "Alarm $id", Recurrence.OneTime, null, true, 9, false, Instant.EPOCH, Instant.EPOCH,
    )
}
