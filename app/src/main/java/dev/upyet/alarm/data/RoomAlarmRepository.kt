package dev.upyet.alarm.data

import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.core.database.AlarmDao
import dev.upyet.core.database.AlarmEntity
import dev.upyet.core.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAlarmRepository
@Inject
constructor(private val alarmDao: AlarmDao, private val timeProvider: TimeProvider) :
    AlarmRepository {
    override fun observeAlarms(): Flow<List<Alarm>> = alarmDao.observeAll().map { alarms ->
        alarms.map(AlarmEntity::toDomain)
    }

    override suspend fun getAlarm(id: AlarmId): Alarm? = alarmDao.findById(id.value)?.toDomain()

    override suspend fun upsert(alarm: Alarm): AlarmId = AlarmId(alarmDao.upsert(alarm.toEntity()))

    override suspend fun setEnabled(id: AlarmId, enabled: Boolean) {
        alarmDao.setEnabled(
            id.value,
            enabled,
            timeProvider.now().toEpochMilli(),
        )
    }

    override suspend fun delete(id: AlarmId) {
        alarmDao.deleteById(id.value)
    }
}
