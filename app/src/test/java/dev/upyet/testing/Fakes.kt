package dev.upyet.testing

import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import dev.upyet.alarm.scheduling.AlarmScheduler
import dev.upyet.alarm.scheduling.SchedulingResult
import dev.upyet.core.directboot.AlarmMirror
import dev.upyet.core.directboot.MirroredAlarm
import dev.upyet.core.directboot.UserUnlockState
import dev.upyet.core.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.ZoneId

class FakeAlarmRepository(initial: List<Alarm> = emptyList()) : AlarmRepository {
    private val alarms = MutableStateFlow(initial)
    val enabledChanges = mutableListOf<Pair<AlarmId, Boolean>>()
    val deleted = mutableListOf<AlarmId>()
    val stored: List<Alarm> get() = alarms.value

    override fun observeAlarms(): Flow<List<Alarm>> = alarms
    override suspend fun getAlarm(id: AlarmId): Alarm? = alarms.value.firstOrNull { it.id == id }
    override suspend fun upsert(alarm: Alarm): AlarmId {
        val id = if (alarm.id.value == 0L) AlarmId((alarms.value.maxOfOrNull { it.id.value } ?: 0L) + 1L) else alarm.id
        alarms.value = (alarms.value.filterNot { it.id == id } + alarm.copy(id = id))
        return id
    }
    override suspend fun setEnabled(id: AlarmId, enabled: Boolean) {
        enabledChanges += id to enabled
        alarms.value = alarms.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }
    override suspend fun delete(id: AlarmId) {
        deleted += id
        alarms.value = alarms.value.filterNot { it.id == id }
    }
}

class FakeAlarmScheduler(var result: SchedulingResult = SchedulingResult.Scheduled) : AlarmScheduler {
    val scheduled = mutableListOf<AlarmId>()
    val cancelled = mutableListOf<Pair<AlarmId, AlarmOccurrenceKind>>()
    override fun schedule(
        alarm: Alarm,
        triggerAt: Instant,
        kind: AlarmOccurrenceKind,
        parentOccurrenceId: OccurrenceId?,
        snoozesRemaining: Int,
    ): SchedulingResult {
        scheduled += alarm.id
        return result
    }
    override fun cancel(alarmId: AlarmId, kind: AlarmOccurrenceKind) {
        cancelled += alarmId to kind
    }
    override fun nextScheduledTrigger(): Instant? = null
}

class FakeAlarmMirror : AlarmMirror {
    override fun put(record: MirroredAlarm) = Unit
    override fun remove(alarmId: AlarmId, kind: AlarmOccurrenceKind) = Unit
    override fun all(): List<MirroredAlarm> = emptyList()
    override fun nextTrigger(): Instant? = null
    override fun clear() = Unit
}

class FixedTimeProvider(
    private val instant: Instant = Instant.parse("2026-08-24T10:00:00Z"),
    private val zone: ZoneId = ZoneId.of("UTC"),
) : TimeProvider {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zone
}

class FakeUserUnlockState(private val unlocked: Boolean = true) : UserUnlockState {
    override fun isUserUnlocked(): Boolean = unlocked
}
