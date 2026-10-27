package dev.upyet.worldclock.data

import android.content.Context
import android.content.res.Resources
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.R
import dev.upyet.core.di.DefaultDispatcher
import dev.upyet.core.logging.AlarmLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Loads the Gazetteer from `res/raw/world_cities.tsv` (ADR 0011).
 *
 * Never throws: unknown zones are filtered instead of crashing. ADR 0011: the prototype called
 * `ZoneId.of` inside `mapIndexed`, and one unknown id blanked the screen. A missing or unreadable
 * resource yields an empty list and a `world_cities_load_failed` log event.
 */
class WorldCitiesLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    suspend fun load(): List<WorldCity> = withContext(dispatcher) {
        try {
            val result = context.resources.openRawResource(R.raw.world_cities).use { stream ->
                stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    WorldCitiesParser.parse(lines, ::resolveDeviceZone)
                }
            }
            AlarmLog.event(
                "world_cities_loaded",
                "rows" to result.cities.size,
                "dropped_unknown_zone" to result.droppedUnknownZone,
                "dropped_malformed" to result.droppedMalformed,
            )
            result.cities
        } catch (e: IOException) {
            AlarmLog.event("world_cities_load_failed", "reason" to e.javaClass.simpleName)
            emptyList()
        } catch (e: Resources.NotFoundException) {
            AlarmLog.event("world_cities_load_failed", "reason" to e.javaClass.simpleName)
            emptyList()
        }
    }
}
