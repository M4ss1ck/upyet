package dev.upyet.worldclock.data

import java.time.DateTimeException
import java.time.ZoneId

/**
 * Result of parsing the Gazetteer TSV. [droppedUnknownZone] counts rows whose zone id this device's
 * tzdb cannot resolve; [droppedMalformed] counts rows that do not match the column contract.
 */
data class WorldCitiesParseResult(val cities: List<WorldCity>, val droppedUnknownZone: Int, val droppedMalformed: Int)

/**
 * Pure parser for `res/raw/world_cities.tsv` (ADR 0011). Never throws: a bad header yields an empty
 * result, and a bad row is counted and skipped.
 */
object WorldCitiesParser {
    const val HEADER = "name\tname_es\tiso_a2\tzone_id\tlatitude\tlongitude\tpopulation\tgeonames_id"

    private const val COLUMNS = 8

    fun parse(lines: Sequence<String>, resolveZone: (String) -> ZoneId?): WorldCitiesParseResult {
        // Single pass over a one-shot sequence (useLines), so every count goes through this iterator.
        val iterator = lines.iterator()
        val first = if (iterator.hasNext()) iterator.next() else null
        if (first != HEADER) {
            // The header is the first line; a file that does not start with it is not this format.
            // Every non-empty line is then unusable, the first line included.
            var nonEmpty = if (first.isNullOrEmpty()) 0 else 1
            while (iterator.hasNext()) {
                if (iterator.next().isNotEmpty()) nonEmpty++
            }
            return WorldCitiesParseResult(emptyList(), droppedUnknownZone = 0, droppedMalformed = nonEmpty)
        }

        val cities = ArrayList<WorldCity>()
        // containsKey, not getOrPut: a null (unknown) zone is cached too, so each id resolves once.
        val zones = HashMap<String, ZoneId?>()
        var unknownZone = 0
        var malformed = 0
        while (iterator.hasNext()) {
            val line = iterator.next()
            if (line.isEmpty()) continue
            val city = parseRow(line)
            if (city == null) {
                malformed++
                continue
            }
            if (!zones.containsKey(city.zoneIdText)) {
                zones[city.zoneIdText] = resolveZone(city.zoneIdText)
            }
            val zone = zones.getValue(city.zoneIdText)
            if (zone == null) {
                unknownZone++
                continue
            }
            cities += city.toWorldCity(zone)
        }
        return WorldCitiesParseResult(cities, droppedUnknownZone = unknownZone, droppedMalformed = malformed)
    }

    private fun parseRow(line: String): ParsedRow? {
        val fields = line.split('\t')
        if (fields.size != COLUMNS) return null
        val latitude = fields[4].toDoubleOrNull() ?: return null
        val longitude = fields[5].toDoubleOrNull() ?: return null
        val population = fields[6].toLongOrNull() ?: return null
        val geonamesId = fields[7].toLongOrNull() ?: return null
        if (geonamesId <= 0) return null
        return ParsedRow(
            name = fields[0],
            nameEs = fields[1],
            isoA2 = fields[2],
            zoneIdText = fields[3],
            latitude = latitude,
            longitude = longitude,
            population = population,
            geonamesId = geonamesId,
        )
    }

    private class ParsedRow(
        val name: String,
        val nameEs: String,
        val isoA2: String,
        val zoneIdText: String,
        val latitude: Double,
        val longitude: Double,
        val population: Long,
        val geonamesId: Long,
    ) {
        fun toWorldCity(zone: ZoneId) = WorldCity(
            name = name,
            nameEs = nameEs,
            isoA2 = isoA2,
            zoneId = zone,
            latitude = latitude,
            longitude = longitude,
            population = population,
            geonamesId = geonamesId,
        )
    }
}

/**
 * Resolves a zone id against this device's tzdb. A device may ship an older tzdb than the one the
 * Gazetteer was built with, so an unknown id returns null instead of throwing.
 */
fun resolveDeviceZone(id: String): ZoneId? = try {
    ZoneId.of(id)
} catch (e: DateTimeException) {
    // ZoneRulesException is a DateTimeException subclass, so this covers unknown ids too.
    null
}
