package dev.upyet.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.upyet.stopwatch.data.AndroidStopwatchNotices
import dev.upyet.stopwatch.data.DataStoreStopwatchStore
import dev.upyet.stopwatch.data.SettingsBootCount
import dev.upyet.stopwatch.domain.BootCount
import dev.upyet.stopwatch.domain.StopwatchNotices
import dev.upyet.stopwatch.domain.StopwatchStore
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the stopwatch's own DataStore file, so it never shares a file with the settings store. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StopwatchDataStore

/**
 * Stopwatch bindings. The stopwatch is credential-protected state (no Direct Boot path), so nothing here is
 * device-protected; it builds its DataStore lazily, on first use after unlock.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StopwatchModule {
    @Binds
    abstract fun bindStopwatchStore(store: DataStoreStopwatchStore): StopwatchStore

    @Binds
    abstract fun bindBootCount(bootCount: SettingsBootCount): BootCount

    @Binds
    abstract fun bindStopwatchNotices(notices: AndroidStopwatchNotices): StopwatchNotices

    companion object {
        @Provides
        @Singleton
        @StopwatchDataStore
        fun provideStopwatchDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = {
                context.preferencesDataStoreFile("stopwatch")
            })
    }
}
