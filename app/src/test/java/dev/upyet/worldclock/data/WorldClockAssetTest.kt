package dev.upyet.worldclock.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Guards the committed world clock assets (ADR 0011). Unit tests run with the `app` module as the
 * working directory, so the paths are module-relative.
 *
 * The sha256 pins below are the reviewed hashes of the committed files. A refresh regenerates the
 * assets with scripts/world-clock/generate.py and updates these pins in the same PR, together with
 * `world_data_manifest.txt`.
 */
class WorldClockAssetTest {
    private val cities = File("src/main/res/raw/world_cities.tsv")
    private val land = File("src/main/res/raw/world_land.bin")
    private val manifest = File("src/main/res/raw/world_data_manifest.txt")

    @Test
    fun citiesFileMatchesPinnedSha256() {
        assertThat(sha256(cities)).isEqualTo(CITIES_SHA256)
    }

    @Test
    fun landFileMatchesPinnedSha256() {
        assertThat(sha256(land)).isEqualTo(LAND_SHA256)
    }

    @Test
    fun manifestRecordsActualSizesAndPins() {
        val lines = manifest.readLines()

        assertThat(lines).contains("world_cities.tsv bytes=${cities.length()} sha256=$CITIES_SHA256")
        assertThat(lines).contains("world_land.bin bytes=${land.length()} sha256=$LAND_SHA256")
    }

    @Test
    fun citiesFormatMatchesContract() {
        val text = cities.readText(Charsets.UTF_8)
        assertThat(text.endsWith("\n")).isTrue()
        val lines = text.removeSuffix("\n").split("\n")

        assertThat(lines.first()).isEqualTo(WorldCitiesParser.HEADER)
        val rows = lines.drop(1)
        assertThat(rows).isNotEmpty()

        val coordinate = Regex("-?\\d+\\.\\d{4}")
        val geonamesIds = mutableListOf<Long>()
        var previousPopulation = Long.MAX_VALUE
        rows.forEachIndexed { index, line ->
            val fields = line.split('\t')
            val where = "row ${index + 2}: $line"
            assertWithMessage(where).that(fields).hasSize(8)
            assertWithMessage(where).that(coordinate.matches(fields[4])).isTrue()
            assertWithMessage(where).that(coordinate.matches(fields[5])).isTrue()
            val population = fields[6].toLong()
            assertWithMessage(where).that(population).isAtMost(previousPopulation)
            previousPopulation = population
            geonamesIds += fields[7].toLong()
        }
        assertWithMessage("geonames_id values must be positive").that(geonamesIds.all { it > 0 }).isTrue()
        assertWithMessage("geonames_id values must be unique").that(geonamesIds.toSet()).hasSize(geonamesIds.size)
    }

    @Test
    fun wholeFileParsesWithoutDroppingAnyRow() {
        val result = cities.bufferedReader(Charsets.UTF_8).useLines { lines ->
            WorldCitiesParser.parse(lines, ::resolveDeviceZone)
        }

        assertThat(result.droppedUnknownZone).isEqualTo(0)
        assertThat(result.droppedMalformed).isEqualTo(0)
        assertThat(result.cities).isNotEmpty()
        val backwardLinks = setOf("Asia/Calcutta", "Europe/Kiev", "America/Buenos_Aires", "Asia/Saigon")
        val offending = result.cities.filter { it.zoneId.id in backwardLinks }
        assertWithMessage("cities on backward-link zone ids").that(offending.map { it.name }).isEmpty()
    }

    @Test
    fun acceptanceCitiesResolveToExpectedZoneAndId() {
        val result = cities.bufferedReader(Charsets.UTF_8).useLines { lines ->
            WorldCitiesParser.parse(lines, ::resolveDeviceZone)
        }
        val byKey = result.cities.associateBy { it.name to it.isoA2 }

        val problems = ACCEPTANCE.mapNotNull { expected ->
            val city = byKey[expected.name to expected.isoA2]
            when {
                city == null -> "${expected.name}/${expected.isoA2}: missing"

                city.zoneId.id != expected.zoneId || city.geonamesId != expected.geonamesId ->
                    "${expected.name}/${expected.isoA2}: got zone=${city.zoneId.id} " +
                        "geonames_id=${city.geonamesId}, want zone=${expected.zoneId} " +
                        "geonames_id=${expected.geonamesId}"

                else -> null
            }
        }
        assertWithMessage("acceptance rows missing or mis-zoned:\n${problems.joinToString("\n")}")
            .that(problems)
            .isEmpty()
    }

    @Test
    fun landBinaryHasExactLayoutAndCoordinateRange() {
        val bytes = land.readBytes()
        assertThat(bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)).isEqualTo("UPLD")

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(4)
        val ringCount = buffer.uint32()
        assertThat(ringCount).isGreaterThan(0L)

        var outOfRange = 0
        repeat(ringCount.toInt()) {
            val pointCount = buffer.uint32()
            repeat(pointCount.toInt()) {
                val lon = buffer.short.toInt()
                val lat = buffer.short.toInt()
                if (lon !in -18000..18000 || lat !in -9000..9000) outOfRange++
            }
        }
        assertWithMessage("points outside lon -18000..18000 / lat -9000..9000").that(outOfRange).isEqualTo(0)
        assertWithMessage("bytes consumed by the ring walk").that(buffer.position()).isEqualTo(bytes.size)
    }

    private fun ByteBuffer.uint32(): Long = getInt().toLong() and 0xFFFFFFFFL

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private data class Acceptance(val name: String, val isoA2: String, val zoneId: String, val geonamesId: Long)

    private companion object {
        // Pins for the committed assets. Refresh = scripts/world-clock/generate.py, then update these
        // in the same PR as world_data_manifest.txt.
        const val CITIES_SHA256 = "bac5b4595dbacf4aaa3fa9e34aae0760a9c98b5a3e139f93dc98946522ada47d"
        const val LAND_SHA256 = "85c2739ee7a83e7d21346e54d435445525139298d8353ab0f099074e3a4aaaef"

        private val ACCEPTANCE = listOf(
            Acceptance("San Francisco", "US", "America/Los_Angeles", 5391959L),
            Acceptance("Barcelona", "ES", "Europe/Madrid", 3128760L),
            Acceptance("Mumbai", "IN", "Asia/Kolkata", 1275339L),
            Acceptance("Osaka", "JP", "Asia/Tokyo", 1853909L),
            Acceptance("Sydney", "AU", "Australia/Sydney", 2147714L),
            Acceptance("Moscow", "RU", "Europe/Moscow", 524901L),
            Acceptance("Houston", "US", "America/Chicago", 4699066L),
            Acceptance("Lagos", "NG", "Africa/Lagos", 2332459L),
            Acceptance("Montevideo", "UY", "America/Montevideo", 5038018L),
            Acceptance("Kiev", "UA", "Europe/Kyiv", 703448L),
        )
    }
}
