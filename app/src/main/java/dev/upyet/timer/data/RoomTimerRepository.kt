package dev.upyet.timer.data

import dev.upyet.core.database.TimerDao
import dev.upyet.core.database.TimerEntity
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Credential-protected: never reachable before first unlock (AGENTS.md §11). Mirroring lands in slice #35. */
@Singleton
class RoomTimerRepository @Inject constructor(private val timerDao: TimerDao) : TimerRepository {
    override fun observeTimers(): Flow<List<Timer>> = timerDao.observeAll().map { timers -> timers.map(TimerEntity::toDomain) }

    override suspend fun getTimer(id: TimerId): Timer? = timerDao.findById(id.value)?.toDomain()

    override suspend fun upsert(timer: Timer): TimerId = TimerId(timerDao.upsert(timer.toEntity()))

    override suspend fun delete(id: TimerId) {
        timerDao.deleteById(id.value)
    }
}
