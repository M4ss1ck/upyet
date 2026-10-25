// PROTOTYPE for #15, throwaway: the real zone data behind the three world clock variants, shared by all of
// them. Nothing here ships - the whole source set is src/debug - so the catalog can be the platform's own:
// ICU knows every canonical zone the device already carries and names its city, java.time answers every
// question about an instant. No bundled city dataset, no network, no Hilt, no Room, no ViewModel.
package dev.upyet.prototype

import android.icu.text.TimeZoneNames
import android.icu.util.TimeZone
import android.icu.util.ULocale
import android.os.Build
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** A canonical zone with the city and country a picker row prints. [country] is empty when ICU knows none. */
internal data class ZoneCity(val zoneId: String, val city: String, val country: String)

/**
 * A place in the user's list. [note] is the alias caveat - "Los Angeles time" - and is null for a city the
 * platform knows by its own name.
 */
internal data class WorldPlace(val zoneId: String, val city: String, val note: String? = null)

/** A city the canonical list does not contain, resolved by hand to the zone whose time it keeps. */
private data class CityAlias(val city: String, val zoneId: String)

/**
 * Fifteen cities the platform's canonical-location list does not contain. Deliberately tiny and hand-made:
 * its job is to make both the gap in the real data and the cost of closing it by hand visible to whoever
 * decides issue #15, so it must never pretend to be a gazetteer.
 *
 * Cities the canonical list already contains are absent on purpose - Sydney, Melbourne, Chicago, Toronto
 * and Rome are all searchable without an entry here.
 */
private val cityAliases = listOf(
    CityAlias("San Francisco", "America/Los_Angeles"),
    CityAlias("Seattle", "America/Los_Angeles"),
    CityAlias("Boston", "America/New_York"),
    CityAlias("Miami", "America/New_York"),
    CityAlias("Houston", "America/Chicago"),
    CityAlias("Munich", "Europe/Berlin"),
    CityAlias("Frankfurt", "Europe/Berlin"),
    CityAlias("Milan", "Europe/Rome"),
    CityAlias("Barcelona", "Europe/Madrid"),
    CityAlias("Mumbai", "Asia/Kolkata"),
    CityAlias("Delhi", "Asia/Kolkata"),
    CityAlias("Osaka", "Asia/Tokyo"),
    CityAlias("Beijing", "Asia/Shanghai"),
    CityAlias("Rio de Janeiro", "America/Sao_Paulo"),
    CityAlias("Tel Aviv", "Asia/Jerusalem"),
)

/** 20:00 to 06:00 is the night the analog grid tints and tags. Local hour, never a colour alone. */
private const val NIGHT_STARTS_AT = 20
private const val NIGHT_ENDS_AT = 6

/**
 * The platform's canonical-location zones, read once per prototype session.
 *
 * Reading is a few hundred ICU lookups on first use - tens of milliseconds on a debug build, which is the
 * price of having the real catalog instead of a fake one, and it buys the decision real coverage: every
 * zone the device can show a time for, with the exact city name ICU prints for it.
 */
internal class ZoneCatalog {
    val cities: List<ZoneCity> by lazy { readCities() }
    private val byId: Map<String, ZoneCity> by lazy { cities.associateBy { it.zoneId } }

    fun city(zoneId: String): ZoneCity? = byId[zoneId]

    private fun readCities(): List<ZoneCity> {
        val names = TimeZoneNames.getInstance(ULocale.getDefault())
        // ICU and java.time read the same tzdb but not the same id set, and this screen prints every row's
        // time through java.time: an id it cannot resolve would be a clock that cannot be drawn, so it is
        // dropped here rather than discovered inside a row.
        val resolvable = ZoneId.getAvailableZoneIds()
        val seen = HashSet<String>()
        return canonicalZoneIds()
            .mapNotNull { id ->
                if (id !in resolvable) return@mapNotNull null
                // No exemplar name means ICU has no city to print for this zone, so there is nothing to show.
                val city = names.getExemplarLocationName(id) ?: return@mapNotNull null
                ZoneCity(zoneId = id, city = city, country = countryName(TimeZone.getRegion(id)))
            }
            // One row per place: below API 28 the id list carries renamed zones, so the same city would
            // otherwise appear twice under two ids that both resolve to the same time.
            .filter { seen.add(it.city.lowercase(Locale.ROOT) + "|" + it.country) }
            .sortedBy { it.city.lowercase(Locale.ROOT) }
    }

    /**
     * Canonical location ids name one zone per place, which is exactly the list a city picker wants.
     * `SystemTimeZoneType.CANONICAL_LOCATION` only exists from API 28, so on 26 and 27 the platform's whole
     * id list is used and the duplicate cities it produces are collapsed by [readCities].
     */
    private fun canonicalZoneIds(): List<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return TimeZone.getAvailableIDs(TimeZone.SystemTimeZoneType.CANONICAL_LOCATION, null, null).toList()
        }
        return TimeZone.getAvailableIDs().toList()
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
 * Collator at PRIMARY strength, which folds exactly these differences for the Latin names ICU hands us,
 * and it answers "contains" without a collation iterator.
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

/** A picker row: the zone to add, the city to print, its country, and the alias caveat when there is one. */
internal data class WorldSearchHit(val zoneId: String, val city: String, val country: String, val note: String?) {
    val label: String get() = if (note == null) city else "$city ($note)"
}

/**
 * Prefix-first search over city and country names, so "sao" finds Sao Paulo and "spain" finds Madrid and
 * Canary. With [withAliases] on, the hand-curated cities come first and print their caveat.
 *
 * Aliases are only offered once something has been typed: an alias table that fills the top of an empty
 * search results screen would hide the very gap it exists to show.
 */
internal fun searchZones(catalog: ZoneCatalog, query: String, withAliases: Boolean): List<WorldSearchHit> {
    val needle = foldForSearch(query.trim())
    val aliasHits =
        if (!withAliases || needle.isEmpty()) {
            emptyList()
        } else {
            cityAliases.mapNotNull { alias ->
                if (!foldForSearch(alias.city).contains(needle)) return@mapNotNull null
                // An alias to a zone this device does not carry would be a dead row.
                val target = catalog.city(alias.zoneId) ?: return@mapNotNull null
                WorldSearchHit(
                    zoneId = alias.zoneId,
                    city = alias.city,
                    country = target.country,
                    note = "${target.city} time",
                )
            }
        }
    val zoneHits =
        catalog.cities
            .mapNotNull { zone ->
                val cityKey = foldForSearch(zone.city)
                val countryKey = foldForSearch(zone.country)
                if (!cityKey.contains(needle) && !countryKey.contains(needle)) return@mapNotNull null
                val prefix = cityKey.startsWith(needle) || countryKey.startsWith(needle)
                WorldSearchHit(zoneId = zone.zoneId, city = zone.city, country = zone.country, note = null) to prefix
            }
            // A stable sort keeps the catalog's alphabetical order inside each of the two groups.
            .sortedByDescending { (_, prefix) -> prefix }
            .map { (hit, _) -> hit }
    return aliasHits + zoneHits
}

/** One place rendered for one instant: what every variant's row or tile prints, computed in one place. */
internal data class CityMoment(val note: String?, val time: ZonedDateTime, val dayLabel: String, val delta: String, val isNight: Boolean)

internal fun WorldPlace.moment(now: Instant, homeZone: ZoneId): CityMoment {
    val time = ZonedDateTime.ofInstant(now, ZoneId.of(zoneId))
    val home = ZonedDateTime.ofInstant(now, homeZone)
    return CityMoment(
        note = note,
        time = time,
        dayLabel = formatDayOffset(ChronoUnit.DAYS.between(home.toLocalDate(), time.toLocalDate())),
        delta = formatZoneDelta(time.offset, home.offset),
        isNight = time.hour >= NIGHT_STARTS_AT || time.hour < NIGHT_ENDS_AT,
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

/** "Tomorrow, +9 h", prefixed by the alias caveat where there is one: "Los Angeles time · Tomorrow, +9 h". */
internal fun CityMoment.detailLine(): String = if (note == null) "$dayLabel, $delta" else "$note · $dayLabel, $delta"

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')
