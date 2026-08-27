package dev.upyet.alarm.scheduling

import android.app.AlarmManager
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.upyet.alarm.playback.ServiceRingingLauncher
import dev.upyet.core.directboot.AlarmMirror
import dev.upyet.core.directboot.AndroidUserUnlockState
import dev.upyet.core.directboot.DirectBootAlarmStore
import dev.upyet.core.directboot.UserUnlockState

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmSchedulingModule {
    @Binds
    abstract fun bindRingingLauncher(launcher: ServiceRingingLauncher): RingingLauncher

    @Binds
    abstract fun bindAlarmScheduler(scheduler: AndroidAlarmScheduler): AlarmScheduler

    @Binds
    abstract fun bindUpcomingAlarmScheduler(scheduler: AndroidUpcomingAlarmScheduler): UpcomingAlarmScheduler

    @Binds
    abstract fun bindAlarmMirror(store: DirectBootAlarmStore): AlarmMirror

    @Binds
    abstract fun bindUserUnlockState(state: AndroidUserUnlockState): UserUnlockState

    companion object {
        @Provides
        fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager = context.getSystemService(AlarmManager::class.java)
    }
}
