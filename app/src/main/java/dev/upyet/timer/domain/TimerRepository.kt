package dev.upyet.timer.domain

import kotlinx.coroutines.flow.Flow

/** Room is the truth for the timer list (ADR 0008); slice #34 implements this and its device-protected mirror. */
interface TimerRepository {
    fun observeTimers(): Flow<List<Timer>>

    suspend fun getTimer(id: TimerId): Timer?

    suspend fun upsert(timer: Timer): TimerId

    suspend fun delete(id: TimerId)
}
