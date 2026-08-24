package dev.myalarm.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.myalarm.core.database.AlarmDao
import dev.myalarm.core.database.EvidenceSegmentDao
import dev.myalarm.core.database.MyAlarmDatabase
import dev.myalarm.core.database.OccurrenceDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MyAlarmDatabase = Room
        .databaseBuilder(
            context,
            MyAlarmDatabase::class.java,
            "myalarm.db",
        ).addMigrations(*MyAlarmDatabase.MIGRATIONS)
        .build()

    @Provides
    fun provideAlarmDao(database: MyAlarmDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideOccurrenceDao(database: MyAlarmDatabase): OccurrenceDao = database.occurrenceDao()

    @Provides
    fun provideEvidenceSegmentDao(database: MyAlarmDatabase): EvidenceSegmentDao = database.evidenceSegmentDao()
}
