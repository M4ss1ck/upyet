package dev.upyet.settings.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class RetentionPolicyTest {
    @Test fun cutoffUsesFixedInstant() {
        val now = Instant.parse("2026-08-24T12:00:00Z")
        assertThat(RetentionPolicy.ONE_DAY.cutoffFrom(now)).isEqualTo(Instant.parse("2026-08-23T12:00:00Z"))
        assertThat(RetentionPolicy.SEVEN_DAYS.cutoffFrom(now)).isEqualTo(Instant.parse("2026-08-17T12:00:00Z"))
        assertThat(RetentionPolicy.FOREVER.cutoffFrom(now)).isNull()
    }
}
