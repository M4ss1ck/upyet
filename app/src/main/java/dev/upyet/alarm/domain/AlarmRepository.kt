package dev.upyet.alarm.domain

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>

    suspend fun getAlarm(id: AlarmId): Alarm?

    suspend fun upsert(alarm: Alarm): AlarmId

    suspend fun setEnabled(id: AlarmId, enabled: Boolean)

    suspend fun setSkipNextOn(id: AlarmId, date: LocalDate?)

    suspend fun delete(id: AlarmId)
}
