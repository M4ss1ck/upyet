package dev.upyet.timer.data

import dev.upyet.core.database.TimerDao
import dev.upyet.core.database.TimerEntity
import dev.upyet.core.directboot.TimerMirror
import dev.upyet.core.directboot.toMirrored
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRepository
import dev.upyet.timer.scheduling.TimerAlarmSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Credential-protected: never reachable before first unlock (AGENTS.md §11). Every write is followed by the
 * device-protected mirror, so the next boot rings what Room holds, and by a re-arm of the one timer alarm
 * (AGENTS.md §6a), so no caller can change a timer and forget the alarm.
 */
@Singleton
class RoomTimerRepository @Inject constructor(
    private val timerDao: TimerDao,
    private val mirror: TimerMirror,
    private val alarms: TimerAlarmSync,
) : TimerRepository {
    override fun observeTimers(): Flow<List<Timer>> = timerDao.observeAll().map { timers -> timers.map(TimerEntity::toDomain) }

    override suspend fun getTimer(id: TimerId): Timer? = timerDao.findById(id.value)?.toDomain()

    override suspend fun upsert(timer: Timer): TimerId {
        val id = TimerId(timerDao.upsert(timer.toEntity()))
        // The alarm outranks the mirror: Room already holds the change, so it is re-armed even if the mirror throws.
        try {
            // Read back rather than mirroring the argument: a new timer only has its id once Room assigned it.
            getTimer(id)?.let { mirror.put(it.toMirrored()) }
        } finally {
            rearm()
        }
        return id
    }

    override suspend fun delete(id: TimerId) {
        timerDao.deleteById(id.value)
        try {
            mirror.remove(id)
        } finally {
            rearm()
        }
    }

    private suspend fun rearm() {
        alarms.sync { observeTimers().first() }
    }
}
