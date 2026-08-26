package dev.upyet.alarm.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VolumeRampTest {
    @Test
    fun startValueIsStartScalar() {
        assertThat(VolumeRamp.scalarAt(0, finalRing = false)).isWithin(1e-6f).of(VolumeRamp.START_SCALAR)
    }

    @Test
    fun midpointIsZeroPointSix() {
        assertThat(VolumeRamp.scalarAt(15_000L, finalRing = false)).isWithin(1e-6f).of(0.6f)
    }

    @Test
    fun endValueIsOne() {
        assertThat(VolumeRamp.scalarAt(VolumeRamp.RAMP_DURATION_MILLIS, finalRing = false)).isWithin(1e-6f).of(1f)
    }

    @Test
    fun beyondEndIsOne() {
        assertThat(VolumeRamp.scalarAt(VolumeRamp.RAMP_DURATION_MILLIS + 10_000L, finalRing = false))
            .isWithin(1e-6f)
            .of(1f)
    }

    @Test
    fun finalRingShortCircuitsAtStart() {
        assertThat(VolumeRamp.scalarAt(0, finalRing = true)).isWithin(1e-6f).of(1f)
    }

    @Test
    fun finalRingShortCircuitsAtMidpoint() {
        assertThat(VolumeRamp.scalarAt(15_000L, finalRing = true)).isWithin(1e-6f).of(1f)
    }

    @Test
    fun finalRingShortCircuitsBeyondEnd() {
        assertThat(VolumeRamp.scalarAt(60_000L, finalRing = true)).isWithin(1e-6f).of(1f)
    }

    @Test
    fun finalRingShortCircuitsNegativeElapsed() {
        assertThat(VolumeRamp.scalarAt(-5_000L, finalRing = true)).isWithin(1e-6f).of(1f)
    }

    @Test
    fun negativeClampsToStartScalar() {
        assertThat(VolumeRamp.scalarAt(-5_000L, finalRing = false)).isWithin(1e-6f).of(VolumeRamp.START_SCALAR)
    }

    @Test
    fun largeNegativeClampsToStartScalar() {
        assertThat(VolumeRamp.scalarAt(-100_000L, finalRing = false)).isWithin(1e-6f).of(VolumeRamp.START_SCALAR)
    }
}
