package dev.upyet.alarm.data

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.Recurrence
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
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
                    true,
                    9,
                    false,
                    createdAt = Instant.ofEpochMilli(100),
                    updatedAt = Instant.ofEpochMilli(200),
                )
            assertThat(
                alarm.toEntity().toDomain(),
            ).isEqualTo(alarm)
        }
    }

    @Test
    fun roundTripsSkipNextOn() {
        val alarm = Alarm(
            AlarmId(42),
            LocalTime.of(7, 30),
            true,
            "Wake",
            Recurrence.Daily,
            null,
            true,
            true,
            9,
            false,
            LocalDate.parse("2027-01-15"),
            Instant.ofEpochMilli(100),
            Instant.ofEpochMilli(200),
        )

        assertThat(alarm.toEntity().toDomain()).isEqualTo(alarm)
        assertThat(alarm.toEntity().skipNextOnEpochDay).isEqualTo(LocalDate.parse("2027-01-15").toEpochDay())
    }

    @Test
    fun roundTripsNullSkipNextOn() {
        val alarm = Alarm(
            AlarmId(42),
            LocalTime.of(7, 30),
            true,
            "Wake",
            Recurrence.Daily,
            null,
            true,
            true,
            9,
            false,
            null,
            Instant.ofEpochMilli(100),
            Instant.ofEpochMilli(200),
        )

        assertThat(alarm.toEntity().toDomain()).isEqualTo(alarm)
        assertThat(alarm.toEntity().skipNextOnEpochDay).isNull()
    }

    @Test
    fun roundTripsSoundDisabledAndPreservesRingtone() {
        val alarm = Alarm(
            AlarmId(42),
            LocalTime.of(7, 30),
            true,
            "Silent",
            Recurrence.Daily,
            "content://media/internal/audio/media/42",
            false,
            true,
            9,
            false,
            LocalDate.parse("2027-01-15"),
            Instant.ofEpochMilli(100),
            Instant.ofEpochMilli(200),
        )

        val roundTripped = alarm.toEntity().toDomain()

        assertThat(roundTripped).isEqualTo(alarm)
        assertThat(roundTripped.soundEnabled).isFalse()
        assertThat(roundTripped.soundUri).isEqualTo("content://media/internal/audio/media/42")
        assertThat(alarm.toEntity().soundEnabled).isFalse()
        assertThat(alarm.toEntity().soundUri).isEqualTo("content://media/internal/audio/media/42")
    }
}
