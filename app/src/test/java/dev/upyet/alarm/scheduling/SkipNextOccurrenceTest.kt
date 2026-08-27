package dev.upyet.alarm.scheduling

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.settings.data.SettingsRepository
import dev.upyet.testing.FakeAlarmMirror
import dev.upyet.testing.FakeAlarmRepository
import dev.upyet.testing.FakeAlarmScheduler
import dev.upyet.testing.FakeDataStore
import dev.upyet.testing.FakeUpcomingAlarmScheduler
import dev.upyet.testing.FakeUserUnlockState
import dev.upyet.testing.FixedTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class SkipNextOccurrenceTest {
    private val timeProvider = FixedTimeProvider()
    private val alarmId = AlarmId(1)

    @Test
    fun skipSetsNextOccurrenceLocalDate() = runTest {
        val alarm = dailyAlarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val skip = skipNextOccurrence(repository)

        val result = skip.skip(alarm)

        assertThat(result).isEqualTo(SchedulingResult.Scheduled)
        assertThat(repository.stored.single().skipNextOn).isEqualTo(LocalDate.of(2026, 8, 24))
    }

    @Test
    fun skipTwiceIsIdempotent() = runTest {
        val alarm = dailyAlarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val skip = skipNextOccurrence(repository)

        skip.skip(alarm)
        val updated = repository.stored.single()
        val second = skip.skip(updated)

        assertThat(second).isEqualTo(SchedulingResult.Scheduled)
        assertThat(repository.stored.single().skipNextOn).isEqualTo(LocalDate.of(2026, 8, 24))
    }

    @Test
    fun unskipClearsIt() = runTest {
        val alarm = dailyAlarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val skip = skipNextOccurrence(repository)

        skip.skip(alarm)
        val skipped = repository.stored.single()
        val result = skip.unskip(skipped)

        assertThat(result).isEqualTo(SchedulingResult.Scheduled)
        assertThat(repository.stored.single().skipNextOn).isNull()
    }

    @Test
    fun oneTimeAlarmReturnsNull() = runTest {
        val alarm = oneTimeAlarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val skip = skipNextOccurrence(repository)

        assertThat(skip.skip(alarm)).isNull()
        assertThat(skip.unskip(alarm)).isNull()
    }

    @Test
    fun disabledAlarmReturnsNull() = runTest {
        val alarm = dailyAlarm(enabled = false)
        val repository = FakeAlarmRepository(listOf(alarm))
        val skip = skipNextOccurrence(repository)

        assertThat(skip.skip(alarm)).isNull()
        assertThat(skip.unskip(alarm)).isNull()
    }

    private fun skipNextOccurrence(repository: FakeAlarmRepository): SkipNextOccurrence {
        val rescheduler = AlarmRescheduler(
            repository,
            FakeAlarmScheduler(),
            timeProvider,
            FakeUserUnlockState(),
            FakeAlarmMirror(),
            SettingsRepository(FakeDataStore()),
            FakeUpcomingAlarmScheduler(),
        )
        return SkipNextOccurrence(repository, timeProvider, rescheduler)
    }

    private fun dailyAlarm(enabled: Boolean) = Alarm(
        alarmId,
        LocalTime.of(11, 0),
        enabled,
        "label",
        Recurrence.Daily,
        null,
        true,
        true,
        9,
        false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun oneTimeAlarm(enabled: Boolean) = Alarm(
        alarmId,
        LocalTime.of(11, 0),
        enabled,
        "label",
        Recurrence.OneTime,
        null,
        true,
        true,
        9,
        false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
