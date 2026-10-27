package dev.upyet.worldclock.domain

import com.google.common.truth.Truth.assertThat
import dev.upyet.worldclock.data.WorldCity
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class WorldClockRowsTest {
    private fun city(name: String, iso: String, zone: String, population: Long, geonamesId: Long) =
        WorldCity(name, name, iso, ZoneId.of(zone), 0.0, 0.0, population, geonamesId)

    private val madrid = city("Madrid", "ES", "Europe/Madrid", 3_255_944, 3117735)
    private val barcelona = city("Barcelona", "ES", "Europe/Madrid", 4_920_000, 3128760)
    private val mumbai = city("Mumbai", "IN", "Asia/Kolkata", 18_978_000, 1275339)
    private val tokyo = city("Tokyo", "JP", "Asia/Tokyo", 35_676_000, 1850147)
    private val losAngeles = city("Los Angeles", "US", "America/Los_Angeles", 3_900_000, 5368361)
    private val newYork = city("New York", "US", "America/New_York", 19_040_000, 5128581)
    private val london = city("London", "GB", "Europe/London", 8_900_000, 2643743)

    private fun rowsOf(ids: List<Long>, now: String, home: String, vararg cities: WorldCity): List<WorldClockRow> =
        worldClockRows(ids, cities.associateBy { it.geonamesId }, Instant.parse(now), ZoneId.of(home))

    private fun WorldClockRow.place(): WorldClockRow.Place = this as WorldClockRow.Place

    @Test
    fun sameZoneHasNoDayOrOffsetDifference() {
        val row = rowsOf(listOf(madrid.geonamesId), "2026-01-15T12:00:00Z", "Europe/Madrid", madrid).single().place()

        assertThat(row.localTime).isEqualTo(LocalDateTime.of(2026, 1, 15, 13, 0))
        assertThat(row.dayDelta).isEqualTo(0)
        assertThat(row.offsetMinutes).isEqualTo(0)
    }

    @Test
    fun kolkataIsFourHoursThirtyMinutesAheadOfMadridInWinter() {
        val row = rowsOf(listOf(mumbai.geonamesId), "2026-01-15T12:00:00Z", "Europe/Madrid", mumbai).single().place()

        assertThat(row.localTime).isEqualTo(LocalDateTime.of(2026, 1, 15, 17, 30))
        assertThat(row.offsetMinutes).isEqualTo(270)
        assertThat(row.dayDelta).isEqualTo(0)
    }

    @Test
    fun tokyoAheadAcrossMidnightIsOneDayAhead() {
        val row = rowsOf(listOf(tokyo.geonamesId), "2026-01-15T18:00:00Z", "Europe/Madrid", tokyo).single().place()

        assertThat(row.localTime).isEqualTo(LocalDateTime.of(2026, 1, 16, 3, 0))
        assertThat(row.dayDelta).isEqualTo(1)
        assertThat(row.offsetMinutes).isEqualTo(480)
    }

    @Test
    fun losAngelesBehindAcrossMidnightIsOneDayBehind() {
        val row = rowsOf(listOf(losAngeles.geonamesId), "2026-01-15T06:00:00Z", "Europe/Madrid", losAngeles).single().place()

        assertThat(row.localTime).isEqualTo(LocalDateTime.of(2026, 1, 14, 22, 0))
        assertThat(row.dayDelta).isEqualTo(-1)
        assertThat(row.offsetMinutes).isEqualTo(-540)
    }

    @Test
    fun newYorkIsFourHoursBehindLondonWhenOnlyTheUsIsInDaylightSaving() {
        // US DST began 2026-03-08; UK DST begins 2026-03-29.
        val row = rowsOf(listOf(newYork.geonamesId), "2026-03-15T12:00:00Z", "Europe/London", newYork).single().place()

        assertThat(row.offsetMinutes).isEqualTo(-240)
    }

    @Test
    fun newYorkIsFiveHoursBehindLondonWhenBothAreInDaylightSaving() {
        val row = rowsOf(listOf(newYork.geonamesId), "2026-04-15T12:00:00Z", "Europe/London", newYork).single().place()

        assertThat(row.offsetMinutes).isEqualTo(-300)
    }

    @Test
    fun unknownIdIsKeptInPlaceAsUnavailable() {
        val rows = rowsOf(
            listOf(madrid.geonamesId, 99L, tokyo.geonamesId),
            "2026-01-15T12:00:00Z",
            "Europe/Madrid",
            madrid,
            tokyo,
        )

        assertThat(rows.map { it.geonamesId }).containsExactly(madrid.geonamesId, 99L, tokyo.geonamesId).inOrder()
        assertThat(rows[0]).isInstanceOf(WorldClockRow.Place::class.java)
        assertThat(rows[1]).isEqualTo(WorldClockRow.Unavailable(99L))
        assertThat(rows[2]).isInstanceOf(WorldClockRow.Place::class.java)
    }

    @Test
    fun orderFollowsTheIdsNotTheCityMap() {
        val rows = rowsOf(listOf(tokyo.geonamesId, madrid.geonamesId), "2026-01-15T12:00:00Z", "Europe/Madrid", madrid, tokyo)

        assertThat(rows.map { it.geonamesId }).containsExactly(tokyo.geonamesId, madrid.geonamesId).inOrder()
    }

    @Test
    fun homeCityIsTheMostPopulousPlaceInTheHomeZone() {
        val home = homeCity(listOf(madrid, tokyo, barcelona, mumbai), ZoneId.of("Europe/Madrid"))

        assertThat(home).isEqualTo(barcelona)
    }

    @Test
    fun homeCityBreaksPopulationTiesByGeonamesIdAscending() {
        val twinA = city("Twin A", "ES", "Europe/Madrid", 500_000, 42)
        val twinB = city("Twin B", "ES", "Europe/Madrid", 500_000, 7)

        assertThat(homeCity(listOf(twinA, twinB), ZoneId.of("Europe/Madrid"))).isEqualTo(twinB)
    }

    @Test
    fun homeCityIsNullWhenTheZoneHasNoPlace() {
        assertThat(homeCity(listOf(tokyo, mumbai), ZoneId.of("Europe/Madrid"))).isNull()
        assertThat(homeCity(emptyList(), ZoneId.of("Europe/Madrid"))).isNull()
    }

    @Test
    fun offsetPartsSplitsSignHoursAndMinutes() {
        assertThat(offsetParts(-330)).isEqualTo(OffsetParts(-1, 5, 30))
        assertThat(offsetParts(0)).isEqualTo(OffsetParts(0, 0, 0))
        assertThat(offsetParts(60)).isEqualTo(OffsetParts(1, 1, 0))
        assertThat(offsetParts(45)).isEqualTo(OffsetParts(1, 0, 45))
    }
}
