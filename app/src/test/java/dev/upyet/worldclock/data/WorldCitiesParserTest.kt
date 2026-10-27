package dev.upyet.worldclock.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

class WorldCitiesParserTest {
    private val header = WorldCitiesParser.HEADER
    private val madrid = "Madrid\tMadrid\tES\tEurope/Madrid\t40.4165\t-3.7026\t3255944\t3117735"
    private val knownZones = setOf("Europe/Madrid", "Asia/Kolkata", "America/Los_Angeles")

    private fun resolve(id: String): ZoneId? = if (id in knownZones) ZoneId.of(id) else null

    private fun parse(vararg lines: String, resolver: (String) -> ZoneId? = ::resolve) =
        WorldCitiesParser.parse(sequenceOf(*lines), resolver)

    @Test
    fun validRowIsParsedIntoEveryField() {
        val result = parse(header, "Mumbai\tBombay\tIN\tAsia/Kolkata\t19.0189\t72.8550\t18978000\t1275339")

        assertThat(result.droppedUnknownZone).isEqualTo(0)
        assertThat(result.droppedMalformed).isEqualTo(0)
        assertThat(result.cities).containsExactly(
            WorldCity(
                name = "Mumbai",
                nameEs = "Bombay",
                isoA2 = "IN",
                zoneId = ZoneId.of("Asia/Kolkata"),
                latitude = 19.0189,
                longitude = 72.8550,
                population = 18978000L,
                geonamesId = 1275339L,
            ),
        ).inOrder()
    }

    @Test
    fun rowWithRejectedZoneIsDroppedAndNeighboursSurvive() {
        val result = parse(
            header,
            madrid,
            "Nowhere\tNowhere\tXX\tNot/AZone\t1.0000\t2.0000\t100\t42",
            "Mumbai\tBombay\tIN\tAsia/Kolkata\t19.0189\t72.8550\t18978000\t1275339",
        )

        assertThat(result.droppedUnknownZone).isEqualTo(1)
        assertThat(result.droppedMalformed).isEqualTo(0)
        assertThat(result.cities.map { it.geonamesId }).containsExactly(3117735L, 1275339L).inOrder()
    }

    @Test
    fun rowsWithWrongShapeOrValuesAreCountedAsMalformed() {
        val result = parse(
            header,
            "Short\tS\tXX\tEurope/Madrid\t1.0000\t2.0000\t100",
            "Long\tL\tXX\tEurope/Madrid\t1.0000\t2.0000\t100\t42\textra",
            "BadLat\tB\tXX\tEurope/Madrid\tnorth\t2.0000\t100\t43",
            "BadPop\tB\tXX\tEurope/Madrid\t1.0000\t2.0000\tmany\t44",
            "ZeroId\tZ\tXX\tEurope/Madrid\t1.0000\t2.0000\t100\t0",
            "NegativeId\tN\tXX\tEurope/Madrid\t1.0000\t2.0000\t100\t-1",
            madrid,
        )

        assertThat(result.droppedMalformed).isEqualTo(6)
        assertThat(result.droppedUnknownZone).isEqualTo(0)
        assertThat(result.cities.map { it.name }).containsExactly("Madrid")
    }

    @Test
    fun wrongHeaderYieldsEmptyResultWithoutThrowing() {
        val result = parse("name\tcity\tiso", madrid, "", madrid)

        assertThat(result.cities).isEmpty()
        assertThat(result.droppedUnknownZone).isEqualTo(0)
        // Every non-empty line is counted, the header line included.
        assertThat(result.droppedMalformed).isEqualTo(3)
    }

    @Test
    fun emptyInputYieldsEmptyResult() {
        val result = WorldCitiesParser.parse(emptySequence(), ::resolve)

        assertThat(result.cities).isEmpty()
        assertThat(result.droppedUnknownZone).isEqualTo(0)
        assertThat(result.droppedMalformed).isEqualTo(0)
    }

    @Test
    fun blankLinesAreSkippedWithoutBeingCounted() {
        val result = parse(header, "", madrid, "")

        assertThat(result.cities).hasSize(1)
        assertThat(result.droppedMalformed).isEqualTo(0)
    }

    @Test
    fun resolverIsCalledOncePerDistinctZoneIncludingUnknownZones() {
        val calls = mutableMapOf<String, Int>()
        val counting: (String) -> ZoneId? = { id ->
            calls[id] = (calls[id] ?: 0) + 1
            resolve(id)
        }

        val result = parse(
            header,
            madrid,
            "Madrid2\tM\tES\tEurope/Madrid\t40.4165\t-3.7026\t100\t3117736",
            "Nowhere\tN\tXX\tNot/AZone\t1.0000\t2.0000\t100\t42",
            "Nowhere2\tN\tXX\tNot/AZone\t1.0000\t2.0000\t100\t43",
            resolver = counting,
        )

        assertThat(calls).containsExactly("Europe/Madrid", 1, "Not/AZone", 1).inOrder()
        assertThat(result.cities).hasSize(2)
        assertThat(result.droppedUnknownZone).isEqualTo(2)
    }

    @Test
    fun resolveDeviceZoneReturnsNullForUnknownId() {
        assertThat(resolveDeviceZone("Not/AZone")).isNull()
    }

    @Test
    fun resolveDeviceZoneReturnsZoneForKnownId() {
        assertThat(resolveDeviceZone("Europe/Madrid")).isEqualTo(ZoneId.of("Europe/Madrid"))
    }
}
