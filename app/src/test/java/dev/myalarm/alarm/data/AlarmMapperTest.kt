package dev.myalarm.alarm.data

import com.google.common.truth.Truth.assertThat
import dev.myalarm.alarm.domain.Alarm
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.Recurrence
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class AlarmMapperTest {
    @Test
    fun roundTripsEveryRecurrence() {
        val recurrences =
            listOf(
                Recurrence.OneTime,
                Recurrence.Daily,
                Recurrence.Weekly(
                    java.time.DayOfWeek.entries
                        .toSet(),
                ),
            )
        recurrences.forEach { recurrence ->
            val alarm =
                Alarm(
                    AlarmId(42),
                    LocalTime.of(7, 30),
                    true,
                    "Wake",
                    recurrence,
                    null,
                    true,
                    9,
                    false,
                    Instant.ofEpochMilli(100),
                    Instant.ofEpochMilli(200),
                )
            assertThat(
                alarm.toEntity().toDomain(),
            ).isEqualTo(alarm)
        }
    }
}
