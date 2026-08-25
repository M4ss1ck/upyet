package dev.upyet.alarm.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class SnoozeCalculatorTest {
    @Test
    fun snoozeAddsMinutesToCurrentInstant() {
        val now = Instant.parse("2027-01-10T07:30:00Z")

        val result = SnoozeCalculator.snoozeAt(now, 10)

        assertThat(
            result,
        ).isEqualTo(Instant.parse("2027-01-10T07:40:00Z"))
    }
}
