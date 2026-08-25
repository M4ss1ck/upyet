package dev.upyet.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.upyet.core.database.AlarmDao
import dev.upyet.core.database.EvidenceSegmentDao
import dev.upyet.core.database.OccurrenceDao
import dev.upyet.core.database.UpYetDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): UpYetDatabase = Room
        .databaseBuilder(
            context,
            UpYetDatabase::class.java,
            "upyet.db",
        ).addMigrations(*UpYetDatabase.MIGRATIONS)
        .build()

    @Provides
    fun provideAlarmDao(database: UpYetDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideOccurrenceDao(database: UpYetDatabase): OccurrenceDao = database.occurrenceDao()

    @Provides
    fun provideEvidenceSegmentDao(database: UpYetDatabase): EvidenceSegmentDao = database.evidenceSegmentDao()
}
