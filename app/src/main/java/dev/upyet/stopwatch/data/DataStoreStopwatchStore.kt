package dev.upyet.stopwatch.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.upyet.core.di.StopwatchDataStore
import dev.upyet.stopwatch.domain.Stopwatch
import dev.upyet.stopwatch.domain.StopwatchStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

private val ACCUMULATED_MS = longPreferencesKey("accumulated_ms")
private val RUNNING_SINCE_MS = longPreferencesKey("running_since_ms")
private val BOOT_COUNT = intPreferencesKey("boot_count")
private val LAPS_MS = stringPreferencesKey("laps_ms")

/**
 * The stopwatch in its own DataStore file. [update] runs the transform inside `updateData`, so a read-modify-write
 * cannot interleave with another. An idle stopwatch is stored as no keys at all, so a reset leaves nothing behind.
 */
@Singleton
class DataStoreStopwatchStore @Inject constructor(@StopwatchDataStore private val dataStore: DataStore<Preferences>) : StopwatchStore {
    override val stopwatch: Flow<Stopwatch> = dataStore.data.map { decodeStopwatch(it) }

    override suspend fun update(transform: (Stopwatch) -> Stopwatch): Stopwatch {
        var next = Stopwatch.IDLE
        dataStore.updateData { prefs ->
            next = transform(decodeStopwatch(prefs))
            encodeStopwatch(next)
        }
        return next
    }
}

/** Writes [stopwatch] as keys. Idle is the empty set of keys, so it clears every key a running stopwatch used. */
internal fun encodeStopwatch(stopwatch: Stopwatch): Preferences {
    if (stopwatch.isIdle) return emptyPreferences()
    return mutablePreferencesOf().apply {
        this[ACCUMULATED_MS] = stopwatch.accumulated.toMillis()
        stopwatch.runningSince?.let { this[RUNNING_SINCE_MS] = it.toMillis() }
        stopwatch.bootCount?.let { this[BOOT_COUNT] = it }
        if (stopwatch.laps.isNotEmpty()) {
            this[LAPS_MS] = stopwatch.laps.joinToString(",") { it.toMillis().toString() }
        }
    }
}

internal fun decodeStopwatch(prefs: Preferences): Stopwatch = Stopwatch(
    accumulated = Duration.ofMillis(prefs[ACCUMULATED_MS] ?: 0L),
    runningSince = prefs[RUNNING_SINCE_MS]?.let { Duration.ofMillis(it) },
    bootCount = prefs[BOOT_COUNT],
    laps = decodeLaps(prefs[LAPS_MS]),
)

/** One malformed entry discards the whole list: a partial lap history would show wrong splits. */
internal fun decodeLaps(raw: String?): List<Duration> {
    if (raw.isNullOrEmpty()) return emptyList()
    val millis = raw.split(',').map { it.toLongOrNull() ?: return emptyList() }
    return millis.map { Duration.ofMillis(it) }
}
