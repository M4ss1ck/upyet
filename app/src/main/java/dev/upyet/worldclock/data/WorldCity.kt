package dev.upyet.worldclock.data

import java.time.ZoneId

/**
 * One Gazetteer row (ADR 0011): a city the World clock can offer, with its IANA zone.
 *
 * [geonamesId] is the stable key the world clock list persists. Display names and zones may
 * change across data refreshes; the GeoNames id does not.
 */
data class WorldCity(
    val name: String,
    val nameEs: String,
    val isoA2: String,
    val zoneId: ZoneId,
    val latitude: Double,
    val longitude: Double,
    val population: Long,
    val geonamesId: Long,
)
