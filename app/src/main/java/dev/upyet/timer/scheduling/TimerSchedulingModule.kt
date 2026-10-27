package dev.upyet.timer.scheduling

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Every binding here is Direct-Boot safe: none of them builds Room or DataStore (AGENTS.md §11, §14). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimerSchedulingModule {
    @Binds
    abstract fun bindTimerRearm(discovery: TimerDiscovery): TimerRearm

    @Binds
    abstract fun bindTimerEditor(discovery: TimerDiscovery): TimerEditor

    @Binds
    abstract fun bindTimerScheduler(scheduler: AndroidTimerScheduler): TimerScheduler

    @Binds
    abstract fun bindTimerMissedNotices(notices: AndroidTimerMissedNotices): TimerMissedNotices
}
