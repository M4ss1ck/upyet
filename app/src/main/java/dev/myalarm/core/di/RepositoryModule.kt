package dev.myalarm.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.myalarm.alarm.data.RoomAlarmRepository
import dev.myalarm.alarm.domain.AlarmRepository
import dev.myalarm.core.time.SystemTimeProvider
import dev.myalarm.core.time.TimeProvider
import dev.myalarm.evidence.data.AppEvidenceFileStore
import dev.myalarm.evidence.data.EvidenceFileStore
import dev.myalarm.evidence.data.RoomOccurrenceRepository
import dev.myalarm.evidence.domain.OccurrenceRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindAlarmRepository(repository: RoomAlarmRepository): AlarmRepository

    @Binds
    abstract fun bindOccurrenceRepository(repository: RoomOccurrenceRepository): OccurrenceRepository

    @Binds
    abstract fun bindEvidenceFileStore(store: AppEvidenceFileStore): EvidenceFileStore

    @Binds
    abstract fun bindTimeProvider(provider: SystemTimeProvider): TimeProvider
}
