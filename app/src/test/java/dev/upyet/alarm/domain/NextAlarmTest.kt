package dev.upyet.alarm.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
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

    private fun alarm(id: Long, time: LocalTime, enabled: Boolean) = Alarm(
        AlarmId(id), time, enabled, "Alarm $id", Recurrence.OneTime, null, true, 9, false, Instant.EPOCH, Instant.EPOCH,
    )
}
