package dev.myalarm.alarm.scheduling

import com.google.common.truth.Truth.assertThat
import dev.myalarm.alarm.domain.Alarm
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.AlarmRepository
import dev.myalarm.alarm.domain.Recurrence
import dev.myalarm.core.directboot.AlarmMirror
import dev.myalarm.core.directboot.MirroredAlarm
import dev.myalarm.core.directboot.UserUnlockState
import dev.myalarm.core.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
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
        val mirror = FakeMirror(MirroredAlarm(AlarmId(1), AlarmOccurrenceKind.MAIN, now.plusSeconds(3600), 5, true, null))
        val report = AlarmRescheduler(repository, FakeScheduler(), FixedTimeProvider(), FakeUnlock(false), mirror).rescheduleAll()
        assertThat(repository.queried).isFalse()
        assertThat(report.scheduled).isEqualTo(1)
    }

    private fun rescheduler(repository: FakeRepository, scheduler: FakeScheduler) =
        AlarmRescheduler(repository, scheduler, FixedTimeProvider(), FakeUnlock(true), FakeMirror())
    private fun alarm(id: Long, enabled: Boolean) =
        Alarm(AlarmId(id), LocalTime.of(11, 0), enabled, "label", Recurrence.OneTime, null, true, 5, false, now, now)

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
        override suspend fun delete(id: AlarmId) = Unit
    }

    private class FakeScheduler(var result: SchedulingResult = SchedulingResult.Scheduled) : AlarmScheduler {
        val cancelled = mutableListOf<Pair<AlarmId, AlarmOccurrenceKind>>()
        override fun schedule(
            alarm: Alarm,
            triggerAt: Instant,
            kind: AlarmOccurrenceKind,
            parentOccurrenceId: dev.myalarm.alarm.domain.OccurrenceId?,
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
