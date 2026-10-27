package dev.upyet.worldclock.domain

import com.google.common.truth.Truth.assertThat
import dev.upyet.worldclock.data.WorldCity
import org.junit.Test
import java.time.ZoneId

class CitySearchIndexTest {
    private val countries = mapOf(
        "BR" to listOf("Brazil"),
        "CH" to listOf("Switzerland"),
        "CL" to listOf("Chile"),
        "DE" to listOf("Germany"),
        "ES" to listOf("Spain"),
        "JP" to listOf("Japan"),
        "KR" to listOf("South Korea"),
        "VE" to listOf("Venezuela"),
    )

    private fun city(name: String, nameEs: String, iso: String, population: Long, geonamesId: Long) =
        WorldCity(name, nameEs, iso, ZoneId.of("UTC"), 0.0, 0.0, population, geonamesId)

    private fun indexOf(cities: List<WorldCity>) = CitySearchIndex(cities) { iso -> countries[iso].orEmpty() }

    private fun CitySearchIndex.ids(query: String): List<Long> = search(query).map { it.geonamesId }

    @Test
    fun normalizeFoldsAccentsCaseAndPunctuation() {
        assertThat(CitySearchIndex.normalize("São Paulo")).isEqualTo("sao paulo")
        assertThat(CitySearchIndex.normalize("MÜNCHEN")).isEqualTo("munchen")
        assertThat(CitySearchIndex.normalize("  Stratford-upon-Avon,  UK ")).isEqualTo("stratford upon avon uk")
        assertThat(CitySearchIndex.normalize("--!!")).isEmpty()
    }

    @Test
    fun accentsAreIgnoredInBothDirections() {
        val index = indexOf(
            listOf(
                city("São Paulo", "São Paulo", "BR", 12_300_000, 3448433),
                city("Zürich", "Zúrich", "CH", 400_000, 2657399),
                city("München", "Múnich", "DE", 1_500_000, 2867714),
            ),
        )

        assertThat(index.ids("sao paulo")).containsExactly(3448433L)
        assertThat(index.ids("zurich")).containsExactly(2657399L)
        assertThat(index.ids("MÜNCHEN")).containsExactly(2867714L)
        assertThat(index.ids("munchen")).containsExactly(2867714L)
        assertThat(index.ids("Múnich")).containsExactly(2867714L)
    }

    @Test
    fun spanishNameMatches() {
        val index = indexOf(listOf(city("Tokyo", "Tokio", "JP", 35_676_000, 1850147)))

        assertThat(index.ids("Tokio")).containsExactly(1850147L)
        assertThat(index.ids("tok")).containsExactly(1850147L)
    }

    @Test
    fun countryNameMatchesEveryCityOfThatCountryByPopulation() {
        val index = indexOf(
            listOf(
                city("Osaka", "Osaka", "JP", 2_700_000, 1853909),
                city("Tokyo", "Tokio", "JP", 13_900_000, 1850147),
                city("Madrid", "Madrid", "ES", 3_300_000, 3117735),
                city("Yokohama", "Yokohama", "JP", 3_700_000, 1848354),
            ),
        )

        assertThat(index.ids("japan")).containsExactly(1850147L, 1848354L, 1853909L).inOrder()
    }

    @Test
    fun cityAndCountryCombinedMatchesOnlyTheRightCity() {
        val index = indexOf(
            listOf(
                city("Barcelona", "Barcelona", "ES", 4_920_000, 3128760),
                city("Barcelona", "Barcelona", "VE", 600_000, 3648559),
                city("Osaka", "Osaka", "JP", 2_700_000, 1853909),
            ),
        )

        assertThat(index.ids("barcelona spain")).containsExactly(3128760L)
        assertThat(index.ids("barcelona venezuela")).containsExactly(3648559L)
        assertThat(index.ids("osaka japan")).containsExactly(1853909L)
        assertThat(index.ids("osaka spain")).isEmpty()
    }

    @Test
    fun prefixOutranksWordStartWhichOutranksMidWordMatch() {
        // Pusan has the largest population but "san" is only mid-word there, so it ranks last.
        val index = indexOf(
            listOf(
                city("Pusan", "Busan", "KR", 9_000_000, 1838524),
                city("Villa San Martin", "Villa San Martin", "CH", 10_000_000, 3000001),
                city("Santiago", "Santiago", "CL", 5_700_000, 3871336),
            ),
        )

        assertThat(index.ids("san")).containsExactly(3871336L, 3000001L, 1838524L).inOrder()
    }

    @Test
    fun withinATierPopulationDescendingThenGeonamesIdAscending() {
        val index = indexOf(
            listOf(
                city("San Jose", "San Jose", "CL", 1_000_000, 4),
                city("San Antonio", "San Antonio", "CL", 1_500_000, 5),
                city("San Luis", "San Luis", "CL", 2_000_000, 30),
                city("San Juan", "San Juan", "CL", 2_000_000, 20),
            ),
        )

        assertThat(index.ids("san")).containsExactly(20L, 30L, 5L, 4L).inOrder()
    }

    @Test
    fun resultsAreCappedAtLimitKeepingTheBestRanked() {
        val index = indexOf(
            listOf(
                city("San Jose", "San Jose", "CL", 1_000_000, 4),
                city("San Luis", "San Luis", "CL", 2_000_000, 30),
                city("San Juan", "San Juan", "CL", 2_000_000, 20),
            ),
        )

        assertThat(index.search("san", limit = 2).map { it.geonamesId }).containsExactly(20L, 30L).inOrder()
    }

    @Test
    fun defaultLimitIsMaxResultsAndIsFifty() {
        val cities = (1..60).map { n -> city("Ville $n", "Ville $n", "CL", n.toLong(), n.toLong()) }

        assertThat(CitySearchIndex.MAX_RESULTS).isEqualTo(50)
        assertThat(indexOf(cities).search("ville")).hasSize(CitySearchIndex.MAX_RESULTS)
    }

    @Test
    fun blankOrPunctuationOnlyQueryReturnsNothing() {
        val index = indexOf(listOf(city("Tokyo", "Tokio", "JP", 35_676_000, 1850147)))

        assertThat(index.search("")).isEmpty()
        assertThat(index.search("   ")).isEmpty()
        assertThat(index.search("--!?")).isEmpty()
    }

    @Test
    fun unmatchedQueryReturnsNothing() {
        val index = indexOf(listOf(city("Tokyo", "Tokio", "JP", 35_676_000, 1850147)))

        assertThat(index.search("zzz")).isEmpty()
    }
}
