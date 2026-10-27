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
 * Loads the map outline from `res/raw/world_land.bin` (ADR 0011). Never throws: a missing, unreadable or
 * malformed asset yields null and a `world_land_load_failed` event, and the screen draws no outline rather
 * than no clocks.
 */
class WorldLandLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    suspend fun load(): WorldLand? = withContext(dispatcher) {
        try {
            val bytes = context.resources.openRawResource(R.raw.world_land).use { it.readBytes() }
            val land = WorldLandDecoder.decode(bytes)
            if (land == null) {
                AlarmLog.event("world_land_load_failed", "reason" to "malformed")
            } else {
                AlarmLog.event("world_land_loaded", "rings" to land.rings.size, "points" to land.pointCount)
            }
            land
        } catch (e: IOException) {
            AlarmLog.event("world_land_load_failed", "reason" to e.javaClass.simpleName)
            null
        } catch (e: Resources.NotFoundException) {
            AlarmLog.event("world_land_load_failed", "reason" to e.javaClass.simpleName)
            null
        }
    }
}
