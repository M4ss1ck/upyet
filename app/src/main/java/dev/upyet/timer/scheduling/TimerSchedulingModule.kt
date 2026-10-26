package dev.upyet.timer.scheduling

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TimerSchedulingModule {
    @Binds
    abstract fun bindTimerRearm(rearm: NoOpTimerRearm): TimerRearm
}
