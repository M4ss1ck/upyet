package dev.upyet.intents

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class IntentsModule {
    @Binds
    abstract fun bindAlarmActions(actions: DefaultAlarmActions): AlarmActions

    @Binds
    abstract fun bindSnoozedAlarms(snoozed: MirrorSnoozedAlarms): SnoozedAlarms
}
