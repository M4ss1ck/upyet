package dev.upyet.worldclock.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** The bundled gazetteer and land outline, loaded once per process and shared by every screen visit. */
interface WorldClockAssets {
    suspend fun cities(): List<WorldCity>
    suspend fun land(): WorldLand?
}

/**
 * Caches each loader's result for the life of the process. The assets are compiled into the APK and cannot change
 * while it runs, so a failed load is cached too: a missing gazetteer yields an empty list every time, not a retry
 * on every screen visit. Each cache has its own mutex so the land outline never waits behind the gazetteer parse.
 */
@Singleton
class BundledWorldClockAssets @Inject constructor(private val citiesLoader: WorldCitiesLoader, private val landLoader: WorldLandLoader) :
    WorldClockAssets {
    private val citiesMutex = Mutex()
    private var cities: List<WorldCity>? = null

    private val landMutex = Mutex()
    private var land: WorldLand? = null
    private var landLoaded = false

    override suspend fun cities(): List<WorldCity> = citiesMutex.withLock {
        cities ?: citiesLoader.load().also { cities = it }
    }

    override suspend fun land(): WorldLand? = landMutex.withLock {
        if (!landLoaded) {
            land = landLoader.load()
            landLoaded = true
        }
        land
    }
}
