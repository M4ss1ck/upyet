package dev.upyet.worldclock.domain

import com.google.common.truth.Truth.assertThat
import dev.upyet.worldclock.data.WorldCitiesParser
import dev.upyet.worldclock.data.resolveDeviceZone
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.util.Locale

/**
 * Runs the search against the committed Gazetteer (ADR 0011), not hand-made fixtures, so a data refresh
 * that reorders the top hits shows up here. Unit tests run with the `app` module as the working directory.
 */
class CitySearchIndexGazetteerTest {
    private val index: CitySearchIndex by lazy {
        val parsed = WorldCitiesParser.parse(
            File("src/main/res/raw/world_cities.tsv").readLines().asSequence(),
            ::resolveDeviceZone,
        )
        // Locale(String, String) is deprecated since JDK 19 and warnings are errors here; the builder is the same region.
        CitySearchIndex(parsed.cities) { iso ->
            listOf(Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.ENGLISH))
        }
    }

    @Test
    fun sanFranciscoFindsTheCaliforniaCityFirst() {
        val first = index.search("san francisco").first()

        assertThat(first.name).isEqualTo("San Francisco")
        assertThat(first.isoA2).isEqualTo("US")
        assertThat(first.zoneId).isEqualTo(ZoneId.of("America/Los_Angeles"))
    }

    @Test
    fun barcelonaFindsTheSpanishCityFirst() {
        val first = index.search("barcelona").first()

        assertThat(first.name).isEqualTo("Barcelona")
        assertThat(first.isoA2).isEqualTo("ES")
        assertThat(first.zoneId).isEqualTo(ZoneId.of("Europe/Madrid"))
    }

    @Test
    fun mumbaiFindsIndiaFirst() {
        val first = index.search("mumbai").first()

        assertThat(first.name).isEqualTo("Mumbai")
        assertThat(first.zoneId).isEqualTo(ZoneId.of("Asia/Kolkata"))
    }

    @Test
    fun osakaFindsJapanFirst() {
        val first = index.search("osaka").first()

        assertThat(first.name).isEqualTo("Osaka")
        assertThat(first.zoneId).isEqualTo(ZoneId.of("Asia/Tokyo"))
    }
}
