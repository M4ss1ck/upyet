package dev.upyet.timer.playback

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.upyet.timer.scheduling.TimerDiscovery
import dev.upyet.timer.scheduling.TimerRingingHandoff

/** Direct-Boot safe like TimerSchedulingModule: TimerDiscovery reaches Room only behind its unlock check (AGENTS.md §11). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimerPlaybackModule {
    @Binds
    abstract fun bindTimerRingingStore(discovery: TimerDiscovery): TimerRingingStore

    @Binds
    abstract fun bindTimerRingingHandoff(handoff: ServiceTimerRingingHandoff): TimerRingingHandoff
}
