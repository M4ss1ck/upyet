package dev.upyet.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.upyet.worldclock.data.BundledWorldClockAssets
import dev.upyet.worldclock.data.WorldClockAssets
import dev.upyet.worldclock.ui.IcuZoneLabeler
import dev.upyet.worldclock.ui.ZoneLabeler

/** World clock bindings: the bundled gazetteer and land outline, and the ICU zone labeler. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorldClockModule {
    @Binds
    abstract fun bindWorldClockAssets(assets: BundledWorldClockAssets): WorldClockAssets

    @Binds
    abstract fun bindZoneLabeler(labeler: IcuZoneLabeler): ZoneLabeler
}
