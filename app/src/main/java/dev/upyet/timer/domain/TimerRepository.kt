package dev.upyet.timer.domain

import kotlinx.coroutines.flow.Flow

/** Room is the truth for the timer list (ADR 0008). Credential-protected: never reach it before first unlock. */
interface TimerRepository {
    fun observeTimers(): Flow<List<Timer>>

    suspend fun getTimer(id: TimerId): Timer?

    suspend fun upsert(timer: Timer): TimerId

    suspend fun delete(id: TimerId)
}
