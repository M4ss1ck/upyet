package dev.upyet.worldclock.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.zone.ZoneOffsetTransition
import java.time.zone.ZoneRules

class ClockChangesTest {
    private val autumn = Instant.parse("2026-11-02T12:00:00Z")

    private fun rules(zone: String): ZoneRules = ZoneId.of(zone).rules

    private fun offsetChange(place: String, home: String, now: Instant = autumn) = nextOffsetChange(rules(place), rules(home), now)

    /** Rules with exactly the given moves, starting at UTC, for cases no real pair of zones produces. */
    private fun moves(vararg moves: Pair<LocalDateTime, Int>): ZoneRules {
        var offset = ZoneOffset.UTC
        val transitions = moves.map { (at, minutes) ->
            val after = ZoneOffset.ofTotalSeconds(offset.totalSeconds + minutes * 60)
            ZoneOffsetTransition.of(at, offset, after).also { offset = after }
        }
        return ZoneRules.of(ZoneOffset.UTC, ZoneOffset.UTC, emptyList(), transitions, emptyList())
    }

    private val fixed: ZoneRules = ZoneOffset.UTC.rules

    @Test
    fun aPlaceChangeMovesTheOffsetByTheChange() {
        assertThat(offsetChange("America/New_York", "Europe/Madrid")).isEqualTo(OffsetChange(60, LocalDate.of(2027, 3, 14)))
    }

    @Test
    fun aHomeChangeMovesTheOffsetTheOtherWayEvenWhenThePlaceNeverChanges() {
        assertThat(offsetChange("Asia/Tokyo", "Europe/Madrid")).isEqualTo(OffsetChange(-60, LocalDate.of(2027, 3, 28)))
    }

    @Test
    fun zonesThatAlwaysChangeTogetherHaveNoOffsetChange() {
        assertThat(offsetChange("Europe/Paris", "Europe/Madrid")).isNull()
    }

    @Test
    fun aZoneComparedWithItselfHasNoOffsetChange() {
        assertThat(offsetChange("Europe/Madrid", "Europe/Madrid")).isNull()
    }

    @Test
    fun twoZonesWithoutClockChangesHaveNoOffsetChange() {
        assertThat(offsetChange("Asia/Tokyo", "Asia/Kolkata")).isNull()
    }

    @Test
    fun aThirtyMinuteChangeIsNotRoundedToAnHour() {
        assertThat(offsetChange("Australia/Lord_Howe", "Australia/Brisbane")).isEqualTo(OffsetChange(-30, LocalDate.of(2027, 4, 4)))
    }

    @Test
    fun oppositeHemispheresTakeWhicheverChangeComesFirst() {
        assertThat(offsetChange("Australia/Sydney", "Europe/Madrid")).isEqualTo(OffsetChange(-60, LocalDate.of(2027, 3, 28)))
        assertThat(offsetChange("Australia/Sydney", "Europe/Madrid", Instant.parse("2027-03-29T00:00:00Z")))
            .isEqualTo(OffsetChange(-60, LocalDate.of(2027, 4, 4)))
    }

    @Test
    fun aPlaceChangeCarriesThePlaceDateEvenWhenHomeIsAlreadyOnTheNextDay() {
        // 11:00 UTC on Mar 14 is 02:00 in Anchorage and already 00:00 on Mar 15 in Auckland.
        assertThat(offsetChange("America/Anchorage", "Pacific/Auckland")).isEqualTo(OffsetChange(60, LocalDate.of(2027, 3, 14)))
    }

    @Test
    fun aHomeChangeCarriesTheHomeDateEvenWhenThePlaceIsStillOnThePreviousDay() {
        // 01:00 UTC on Mar 28 is still Mar 27 in Honolulu.
        assertThat(offsetChange("Pacific/Honolulu", "Europe/Madrid")).isEqualTo(OffsetChange(-60, LocalDate.of(2027, 3, 28)))
    }

    @Test
    fun zonesChangingAtTheSameMomentByDifferentAmountsAreNettedOnTheHomeDate() {
        val at = LocalDateTime.of(2027, 3, 1, 0, 30)
        val place = moves(at to 60)
        val home = moves(at to 30)

        assertThat(nextOffsetChange(place, home, autumn)).isEqualTo(OffsetChange(30, LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun aCancellingPairIsSkippedForTheChangeAfterIt() {
        val together = LocalDateTime.of(2027, 1, 10, 2, 0)
        val alone = LocalDateTime.of(2027, 2, 10, 2, 0)
        val place = moves(together to 60, alone to 60)
        val home = moves(together to 60)

        assertThat(nextOffsetChange(place, home, autumn)).isEqualTo(OffsetChange(60, LocalDate.of(2027, 2, 10)))
    }

    @Test
    fun aChangeOutsideTheTwelveMonthHorizonIsNotReported() {
        val inside = moves(LocalDateTime.of(2027, 11, 2, 11, 59) to 60)
        val onTheEdge = moves(LocalDateTime.of(2027, 11, 2, 12, 0) to 60)

        assertThat(nextOffsetChange(inside, fixed, autumn)).isEqualTo(OffsetChange(60, LocalDate.of(2027, 11, 2)))
        assertThat(nextOffsetChange(onTheEdge, fixed, autumn)).isNull()
        assertThat(nextClockChange(inside, autumn)).isEqualTo(ClockChange(60, LocalDateTime.of(2027, 11, 2, 11, 59)))
        assertThat(nextClockChange(onTheEdge, autumn)).isNull()
    }

    @Test
    fun aHomeClockChangeReportsTheWallClockMomentBeforeIt() {
        assertThat(nextClockChange(rules("Europe/Madrid"), autumn)).isEqualTo(ClockChange(60, LocalDateTime.of(2027, 3, 28, 2, 0)))
        assertThat(nextClockChange(rules("Europe/Madrid"), Instant.parse("2027-04-01T00:00:00Z")))
            .isEqualTo(ClockChange(-60, LocalDateTime.of(2027, 10, 31, 3, 0)))
    }

    @Test
    fun aHomeZoneWithoutClockChangesHasNone() {
        assertThat(nextClockChange(rules("Asia/Tokyo"), autumn)).isNull()
    }

    @Test
    fun aHomeClockChangeReportsAThirtyMinuteMove() {
        assertThat(nextClockChange(rules("Australia/Lord_Howe"), autumn)?.deltaMinutes).isEqualTo(-30)
    }

    @Test
    fun theMomentOfAChangeIsNotItsOwnNextChange() {
        val change = Instant.parse("2027-03-28T01:00:00Z")

        assertThat(nextClockChange(rules("Europe/Madrid"), change)?.deltaMinutes).isEqualTo(-60)
    }
}
