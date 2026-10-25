// PROTOTYPE for #15, throwaway: the bundled city extract and the time math behind the one surviving world
// clock variant. Nothing here ships - the whole source set is src/debug - so the places can come from the
// public-domain Natural Earth extract that prototype-data/convert.py turns into two assets, and java.time
// can answer every question about an instant. No ICU exemplar names, no alias table, no network, no Hilt,
// no Room, no ViewModel: reading the extract once is the whole of the "data layer" here.
package dev.upyet.prototype

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * One place the prototype can list: a row of the bundled extract, with everything a row, a search result or
 * a map marker prints.
 *
 * The two name fields stay side by side rather than being resolved once at load time, because the choice
 * belongs to the device's language, not to the data: a Spanish phone reads "Tokio" and "Nueva York", and the
 * same build on an English phone reads "Tokyo" and "New York" without reloading anything.
 */
internal class WorldCity(
    val nameAscii: String,
    val nameEs: String,
    val isoA2: String,
    val zone: ZoneId,
    val latitude: Double,
    val longitude: Double,
    val popMax: Int,
    /** Localized country name, empty when the extract says "-99" or the platform has no name for it. */
    val country: String,
) {
    // Folded once here, not once per keystroke: a five-thousand-row filter that re-normalises three strings
    // per row per character is felt on a debug build.
    private val foldedName: String = foldForSearch(nameAscii)
    private val foldedNameEs: String = foldForSearch(nameEs)
    private val foldedCountry: String = foldForSearch(country)

    /**
     * Stable identity for list keys and map selection. The extract carries no id column, and coordinates are
     * the one pair that cannot collide between two different places.
     */
    val key: String get() = "$nameAscii|$isoA2|$latitude|$longitude"

    /** "Tokio" on a Spanish device, "Tokyo" everywhere else: the only display rule the extract needs. */
    fun displayName(language: String = Locale.getDefault().language): String =
        if (language == "es" && nameEs.isNotEmpty()) nameEs else nameAscii

    /** "City, Country", or the city alone when the extract has no country for it. */
    fun title(language: String = Locale.getDefault().language): String {
        val name = displayName(language)
        return if (country.isEmpty()) name else "$name, $country"
    }

    /** Case- and accent-insensitive "contains" over both names and the localized country name. */
    fun matches(foldedNeedle: String): Boolean =
        foldedName.contains(foldedNeedle) || foldedNameEs.contains(foldedNeedle) || foldedCountry.contains(foldedNeedle)
}

/** The two assets the prototype reads at launch: the city extract and the land rings. */
internal class WorldClockAssets(val cities: List<WorldCity>, val land: List<FloatArray>)

/**
 * Reads `cities.tsv` and `land.bin` once, off the main thread. Both formats are documented in
 * `app/src/debug/prototype-data/convert.py`; this is the only reader either of them has.
 */
internal object WorldClockAssetsLoader {
    private const val CITIES_ASSET = "prototype/cities.tsv"
    private const val LAND_ASSET = "prototype/land.bin"
    private const val CITIES_COLUMNS = 7
    private const val LAND_MAGIC = "UPLD"

    /** The converter's quantisation step: `round(degrees * 100)` back to degrees. */
    private const val LAND_DEGREE_SCALE = 100f

    suspend fun load(context: Context): WorldClockAssets = withContext(Dispatchers.IO) {
        val cities = context.assets.open(CITIES_ASSET).bufferedReader().use { readCities(it.readText()) }
        val land = context.assets.open(LAND_ASSET).use { readLand(it.readBytes()) }
        WorldClockAssets(cities = cities, land = land)
    }

    /**
     * The TSV is written by the converter with tabs collapsed out of every name, so a tab is always a field
     * separator and the header can be skipped by position rather than by name.
     */
    private fun readCities(text: String): List<WorldCity> {
        val countries = HashMap<String, String>()
        return text
            .lineSequence()
            .filter { it.isNotEmpty() }
            .drop(1)
            .mapIndexed { index, line ->
                val fields = line.split('\t')
                check(fields.size == CITIES_COLUMNS) { "cities.tsv line ${index + 2}: ${fields.size} fields, expected $CITIES_COLUMNS" }
                WorldCity(
                    nameAscii = fields[0],
                    nameEs = fields[1],
                    isoA2 = fields[2],
                    // Validated once here: a zone id the device's tzdb cannot resolve would be a row whose
                    // clock cannot be drawn, discovered inside a row instead of at load time.
                    zone = ZoneId.of(fields[3]),
                    latitude = fields[4].toDouble(),
                    longitude = fields[5].toDouble(),
                    popMax = fields[6].toInt(),
                    country = countries.getOrPut(fields[2]) { countryName(fields[2]) },
                )
            }.toList()
    }

    /** `UPLD`, a ring count, then per ring a point count and little-endian int16 pairs at 1/100 degree. */
    private fun readLand(bytes: ByteArray): List<FloatArray> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { buffer.get(it) }
        check(String(magic, Charsets.US_ASCII) == LAND_MAGIC) { "land.bin: unexpected header" }
        val ringCount = buffer.int
        return List(ringCount) {
            val pointCount = buffer.int
            // Interleaved lon,lat: one FloatArray per ring keeps the whole coastline in two contiguous
            // allocations per point pair instead of one object per vertex.
            FloatArray(pointCount * 2).also { ring ->
                for (index in 0 until pointCount) {
                    ring[index * 2] = buffer.short / LAND_DEGREE_SCALE
                    ring[index * 2 + 1] = buffer.short / LAND_DEGREE_SCALE
                }
            }
        }
    }
}

/** "GB" -> "United Kingdom". Built through Locale.Builder: the Locale(String, String) constructor is deprecated. */
private fun countryName(region: String): String {
    val letters = region.length == 2 && region.all { it in 'A'..'Z' || it in 'a'..'z' }
    if (!letters) return ""
    val country = Locale.Builder().setRegion(region).build().displayCountry
    return if (country == region) "" else country
}

/**
 * Case- and accent-insensitive form of [text]: NFD, combining marks dropped, lower-cased. It stands in for
 * Collator at PRIMARY strength, which folds exactly these differences for the Latin names the extract hands
 * us, and it answers "contains" without a collation iterator.
 */
internal fun foldForSearch(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val folded = StringBuilder(decomposed.length)
    for (character in decomposed) {
        if (Character.getType(character) == Character.NON_SPACING_MARK.toInt()) continue
        folded.append(character.lowercaseChar())
    }
    return folded.toString()
}

/**
 * The places a typed query reaches: every row whose city name, Spanish name or localized country contains it,
 * most populous first.
 *
 * Population order is the whole ranking on purpose. "san" reaches San Antonio, San Diego and San Jose before
 * it reaches a village in the same country, which is the order a person picking a clock expects, and it needs
 * no scoring heuristic to defend.
 */
internal fun searchCities(cities: List<WorldCity>, query: String): List<WorldCity> {
    val needle = foldForSearch(query.trim())
    if (needle.isEmpty()) return emptyList()
    return cities.filter { it.matches(needle) }.sortedByDescending { it.popMax }
}

/** One place rendered for one instant: what a row, a search result and a marker all read from. */
internal data class CityMoment(val time: ZonedDateTime, val dayLabel: String, val delta: String)

internal fun WorldCity.moment(now: Instant, homeZone: ZoneId): CityMoment {
    val time = ZonedDateTime.ofInstant(now, zone)
    val home = ZonedDateTime.ofInstant(now, homeZone)
    return CityMoment(
        time = time,
        dayLabel = formatDayOffset(ChronoUnit.DAYS.between(home.toLocalDate(), time.toLocalDate())),
        delta = formatZoneDelta(time.offset, home.offset),
    )
}

/** "14:05": fixed width on purpose, because a column of times is read down, not in a sentence. */
internal fun formatClock(time: ZonedDateTime): String = "${twoDigits(time.hour)}:${twoDigits(time.minute)}"

/** "Same time", "+5 h", "−3 h 30 min": the sign and the unit are text, so the difference survives a colour-blind reader. */
internal fun formatZoneDelta(cityOffset: ZoneOffset, homeOffset: ZoneOffset): String {
    val minutes = (cityOffset.totalSeconds - homeOffset.totalSeconds) / 60
    if (minutes == 0) return "Same time"
    val magnitude = abs(minutes)
    val hours = magnitude / 60
    val rest = magnitude % 60
    val sign = if (minutes > 0) "+" else "−"
    return when {
        hours == 0 -> "$sign$rest min"
        rest == 0 -> "$sign$hours h"
        else -> "$sign$hours h $rest min"
    }
}

/** "Today" / "Tomorrow" / "Yesterday", with a count for the two extremes the ±26 h of offsets can reach. */
internal fun formatDayOffset(days: Long): String = when {
    days == 0L -> "Today"
    days == 1L -> "Tomorrow"
    days == -1L -> "Yesterday"
    days > 0L -> "$days days ahead"
    else -> "${-days} days behind"
}

/** "Tomorrow, +9 h": the one line under a city that says how far away it is in both senses. */
internal fun CityMoment.detailLine(): String = "$dayLabel, $delta"

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')
