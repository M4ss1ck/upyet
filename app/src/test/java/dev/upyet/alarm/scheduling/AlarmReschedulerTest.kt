package dev.upyet.alarm.scheduling

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.NextAlarm
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.core.directboot.AlarmMirror
import dev.upyet.core.directboot.MirroredAlarm
import dev.upyet.core.directboot.UserUnlockState
import dev.upyet.core.time.TimeProvider
import dev.upyet.testing.FakeAlarmRepository
import dev.upyet.testing.FakeAlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class AlarmReschedulerTest {
    private val now = Instant.parse("2026-08-24T10:00:00Z")

    @Test
    fun schedulesEnabledAndCancelsDisabled() = runTest {
        val enabled = alarm(1, true)
        val disabled = alarm(2, false)
        val repository = FakeRepository(listOf(enabled, disabled))
        val scheduler = FakeScheduler()
        val report = rescheduler(repository, scheduler).rescheduleAll()
        assertThat(report.scheduled).isEqualTo(1)
        assertThat(scheduler.cancelled).containsExactly(AlarmId(2) to AlarmOccurrenceKind.MAIN)
    }

    @Test
    fun reportsFailuresAndExactAlarmUnavailable() = runTest {
        val scheduler = FakeScheduler(SchedulingResult.Failed(IllegalStateException()))
        val report = rescheduler(FakeRepository(listOf(alarm(1, true))), scheduler).rescheduleAll()
        assertThat(report.failures).containsExactly(AlarmId(1))
        scheduler.result = SchedulingResult.ExactAlarmsUnavailable
        val unavailable = rescheduler(FakeRepository(listOf(alarm(1, true))), scheduler).rescheduleAll()
        assertThat(unavailable.exactAlarmsUnavailable).isTrue()
    }

    @Test
    fun lockedPathNeverQueriesRepository() = runTest {
        val repository = FakeRepository(emptyList())
        val mirror = FakeMirror(
            MirroredAlarm(
                alarmId = AlarmId(1),
                kind = AlarmOccurrenceKind.MAIN,
                triggerAt = now.plusSeconds(3600),
                snoozeMinutes = 5,
                vibrationEnabled = true,
                soundUri = null,
                minuteOfDay = 7 * 60,
                recurrenceType = "ONE_TIME",
                weekdayMask = 0,
                snoozesRemaining = dev.upyet.alarm.domain.SnoozeBudget.UNSET,
                chainStartedAtMillis = 0L,
            ),
        )
        val report = AlarmRescheduler(repository, FakeScheduler(), FixedTimeProvider(), FakeUnlock(false), mirror).rescheduleAll()
        assertThat(repository.queried).isFalse()
        assertThat(report.scheduled).isEqualTo(1)
    }

    /**
     * A one-time alarm that has rung must switch itself off. Left enabled, rescheduleAll rolls it to
     * the same time tomorrow and it reappears as the next alarm, which is what "never repeats" is not.
     */
    @Test
    fun aRungOneTimeAlarmIsRetiredAndDoesNotRollToTomorrow() = runTest {
        val repository = FakeAlarmRepository(listOf(alarm(1, true)))
        val scheduler = FakeAlarmScheduler()
        val rescheduler = AlarmRescheduler(repository, scheduler, FixedTimeProvider(), FakeUnlock(true), FakeMirror())

        rescheduler.retireIfOneTime(AlarmId(1))
        rescheduler.rescheduleAll()

        assertThat(repository.stored.single().enabled).isFalse()
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(scheduler.cancelled).containsExactly(AlarmId(1) to AlarmOccurrenceKind.MAIN)
        assertThat(NextAlarm.select(repository.stored, ZoneId.of("UTC"), now)).isNull()
    }

    @Test
    fun retiringLeavesRepeatingAlarmsEnabled() = runTest {
        val daily = alarm(1, true).copy(recurrence = Recurrence.Daily)
        val weekly = alarm(2, true).copy(recurrence = Recurrence.Weekly(setOf(DayOfWeek.MONDAY)))
        val repository = FakeAlarmRepository(listOf(daily, weekly))
        val rescheduler = AlarmRescheduler(repository, FakeAlarmScheduler(), FixedTimeProvider(), FakeUnlock(true), FakeMirror())

        rescheduler.retireIfOneTime(AlarmId(1))
        rescheduler.retireIfOneTime(AlarmId(2))

        assertThat(repository.enabledChanges).isEmpty()
        assertThat(repository.stored.map { it.enabled }).containsExactly(true, true)
    }

    @Test
    fun retiringAnAlarmThatIsAlreadyGoneDoesNothing() = runTest {
        val repository = FakeAlarmRepository(emptyList())
        val rescheduler = AlarmRescheduler(repository, FakeAlarmScheduler(), FixedTimeProvider(), FakeUnlock(true), FakeMirror())

        rescheduler.retireIfOneTime(AlarmId(99))

        assertThat(repository.enabledChanges).isEmpty()
    }

    private fun rescheduler(repository: FakeRepository, scheduler: FakeScheduler) =
        AlarmRescheduler(repository, scheduler, FixedTimeProvider(), FakeUnlock(true), FakeMirror())
    private fun alarm(id: Long, enabled: Boolean) = Alarm(
        AlarmId(
            id,
        ),
        LocalTime.of(11, 0), enabled, "label", Recurrence.OneTime, null, true, 5, false, createdAt = now, updatedAt = now,
    )

    private class FakeRepository(alarms: List<Alarm>) : AlarmRepository {
        private val flow = MutableStateFlow(alarms)
        var queried = false
        override fun observeAlarms(): Flow<List<Alarm>> {
            queried = true
            return flow
        }
        override suspend fun getAlarm(id: AlarmId): Alarm? = flow.value.find { it.id == id }
        override suspend fun upsert(alarm: Alarm) = alarm.id
        override suspend fun setEnabled(id: AlarmId, enabled: Boolean) = Unit
        override suspend fun setSkipNextOn(id: AlarmId, date: java.time.LocalDate?) = Unit
        override suspend fun delete(id: AlarmId) = Unit
    }

    private class FakeScheduler(var result: SchedulingResult = SchedulingResult.Scheduled) : AlarmScheduler {
        val cancelled = mutableListOf<Pair<AlarmId, AlarmOccurrenceKind>>()
        override fun schedule(
            alarm: Alarm,
            triggerAt: Instant,
            kind: AlarmOccurrenceKind,
            parentOccurrenceId: dev.upyet.alarm.domain.OccurrenceId?,
            snoozesRemaining: Int,
            chainStartedAtMillis: Long,
        ) = result
        override fun cancel(alarmId: AlarmId, kind: AlarmOccurrenceKind) {
            cancelled += alarmId to kind
        }
        override fun nextScheduledTrigger(): Instant? = null
    }

    private class FakeMirror(private var record: MirroredAlarm? = null) : AlarmMirror {
        override fun put(record: MirroredAlarm) {
            this.record = record
        }
        override fun remove(alarmId: AlarmId, kind: AlarmOccurrenceKind) = Unit
        override fun all() = listOfNotNull(record)
        override fun nextTrigger() = record?.triggerAt
        override fun clear() = Unit
    }

    private class FixedTimeProvider : TimeProvider {
        override fun now() = Instant.parse("2026-08-24T10:00:00Z")
        override fun zone() = ZoneId.of("UTC")
    }

    private class FakeUnlock(private val unlocked: Boolean) : UserUnlockState {
        override fun isUserUnlocked() = unlocked
    }
}
