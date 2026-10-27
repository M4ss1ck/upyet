package dev.upyet.alarm.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SnoozePolicyTest {
    @Test
    fun offEncodesToZeroAndZeroDecodesBackToOff() {
        assertThat(SnoozePolicy.Off.encode()).isEqualTo(SnoozePolicy.OFF_ENCODED)
        assertThat(SnoozePolicy.decode(SnoozePolicy.OFF_ENCODED)).isEqualTo(SnoozePolicy.Off)
    }

    @Test
    fun afterRoundTripsThroughItsMinutes() {
        listOf(5, 9, 10, 15, 30).forEach { minutes ->
            val policy = SnoozePolicy.After(minutes)

            assertThat(policy.encode()).isEqualTo(minutes)
            assertThat(SnoozePolicy.decode(policy.encode())).isEqualTo(policy)
        }
    }

    @Test
    fun zeroAndNegativeValuesDecodeToOff() {
        assertThat(SnoozePolicy.decode(0)).isEqualTo(SnoozePolicy.Off)
        assertThat(SnoozePolicy.decode(-1)).isEqualTo(SnoozePolicy.Off)
        assertThat(SnoozePolicy.decode(Int.MIN_VALUE)).isEqualTo(SnoozePolicy.Off)
    }

    @Test
    fun afterRejectsNonPositiveMinutes() {
        assertThrows(IllegalArgumentException::class.java) { SnoozePolicy.After(0) }
        assertThrows(IllegalArgumentException::class.java) { SnoozePolicy.After(-5) }
    }
}
