package dev.upyet.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.upyet.core.logging.DiagnosticLogStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
    @Provides
    @Singleton
    fun provideDiagnosticLogStore(@ApplicationContext context: Context): DiagnosticLogStore = DiagnosticLogStore.forApp(context)
}
