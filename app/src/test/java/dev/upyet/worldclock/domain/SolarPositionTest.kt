package dev.upyet.worldclock.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class SolarPositionTest {
    private fun sun(iso: String) = subsolarPoint(Instant.parse(iso))

    @Test
    fun declinationIsNearZeroAtTheEquinoxes() {
        assertThat(sun("2026-03-20T12:00:00Z").latitude).isWithin(1.5).of(0.0)
        assertThat(sun("2026-09-23T12:00:00Z").latitude).isWithin(1.5).of(0.0)
    }

    @Test
    fun declinationReachesTheTropicsAtTheSolstices() {
        assertThat(sun("2026-06-21T12:00:00Z").latitude).isWithin(0.5).of(23.44)
        assertThat(sun("2026-12-21T12:00:00Z").latitude).isWithin(0.5).of(-23.44)
    }

    @Test
    fun subsolarLongitudeFollowsUtcNoon() {
        assertThat(sun("2026-03-20T12:00:00Z").longitude).isWithin(1e-9).of(0.0)
        assertThat(sun("2026-03-20T18:00:00Z").longitude).isWithin(1e-9).of(-90.0)
        assertThat(sun("2026-03-20T06:00:00Z").longitude).isWithin(1e-9).of(90.0)
    }

    @Test
    fun subsolarLongitudeIncludesSecondsAndWrapsIntoRange() {
        // 00:00 UTC is 12 hours from noon, which lands on +180; the range is [-180, 180), so it wraps to -180.
        assertThat(sun("2026-03-20T00:00:00Z").longitude).isWithin(1e-9).of(-180.0)
        // One second past noon moves the sun a quarter degree west of the meridian.
        assertThat(sun("2026-03-20T12:00:01Z").longitude).isWithin(1e-6).of(-15.0 / 3600.0)
    }

    @Test
    fun equinoxNoonDarknessIsOnlyAPolarCapOnTheSunsMeridian() {
        // Declination is about -1 degree on 2026-03-20, so the sliver of darkness sits over the north pole.
        val band = checkNotNull(nightBand(longitude = 0.0, sun = sun("2026-03-20T12:00:00Z")))

        assertThat(band.start).isGreaterThan(85.0)
        assertThat(band.endInclusive).isWithin(0.01).of(90.0)
    }

    @Test
    fun equinoxNoonOppositeMeridianIsAlmostAllNight() {
        val band = checkNotNull(nightBand(longitude = 180.0, sun = sun("2026-03-20T12:00:00Z")))

        assertThat(band.start).isLessThan(-85.0)
        assertThat(band.endInclusive).isGreaterThan(85.0)
    }

    @Test
    fun midsummerMidnightSunLeavesTheArcticLitAtMidnight() {
        // The dark band on the midnight meridian runs from the south pole up to the Arctic Circle's far edge.
        val band = checkNotNull(nightBand(longitude = 180.0, sun = sun("2026-06-21T12:00:00Z")))

        assertThat(band.start).isWithin(1.0).of(-90.0)
        assertThat(band.endInclusive).isAtLeast(60.0)
        assertThat(band.endInclusive).isAtMost(70.0)
    }
}
