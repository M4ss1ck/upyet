package dev.upyet.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.upyet.core.directboot.DirectBootTimerStore
import dev.upyet.core.directboot.TimerMirror
import dev.upyet.core.time.SystemTimerClock
import dev.upyet.core.time.TimerClock
import dev.upyet.timer.data.RoomTimerRepository
import dev.upyet.timer.domain.TimerRepository

/**
 * Timer persistence (ADR 0008). [TimerMirror] and [TimerClock] are Direct-Boot-safe: they build neither Room nor
 * DataStore. [TimerRepository] builds Room, so a pre-unlock path takes it as `Lazy` and gets it only after
 * `UserUnlockState.isUserUnlocked()` (AGENTS.md §11, §14).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimerPersistenceModule {
    @Binds
    abstract fun bindTimerRepository(repository: RoomTimerRepository): TimerRepository

    @Binds
    abstract fun bindTimerMirror(store: DirectBootTimerStore): TimerMirror

    @Binds
    abstract fun bindTimerClock(clock: SystemTimerClock): TimerClock
}
