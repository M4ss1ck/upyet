package dev.myalarm.alarm.scheduling

import android.app.AlarmManager
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.myalarm.alarm.playback.ServiceRingingLauncher
import dev.myalarm.core.directboot.AlarmMirror
import dev.myalarm.core.directboot.AndroidUserUnlockState
import dev.myalarm.core.directboot.DirectBootAlarmStore
import dev.myalarm.core.directboot.UserUnlockState

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmSchedulingModule {
    @Binds
    abstract fun bindRingingLauncher(launcher: ServiceRingingLauncher): RingingLauncher

    @Binds
    abstract fun bindAlarmScheduler(scheduler: AndroidAlarmScheduler): AlarmScheduler

    @Binds
    abstract fun bindAlarmMirror(store: DirectBootAlarmStore): AlarmMirror

    @Binds
    abstract fun bindUserUnlockState(state: AndroidUserUnlockState): UserUnlockState

    companion object {
        @Provides
        fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager = context.getSystemService(AlarmManager::class.java)
    }
}
