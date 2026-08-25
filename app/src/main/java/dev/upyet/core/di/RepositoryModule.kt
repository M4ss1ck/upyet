package dev.upyet.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.upyet.alarm.data.RoomAlarmRepository
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.core.time.SystemTimeProvider
import dev.upyet.core.time.TimeProvider
import dev.upyet.evidence.data.AppEvidenceFileStore
import dev.upyet.evidence.data.EvidenceFileStore
import dev.upyet.evidence.data.RoomOccurrenceRepository
import dev.upyet.evidence.domain.OccurrenceRepository

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
