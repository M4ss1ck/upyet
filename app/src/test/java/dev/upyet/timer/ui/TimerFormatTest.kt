package dev.upyet.timer.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class TimerFormatTest {
    @Test fun belowAnHourOmitsHours() {
        assertThat(formatTimerDuration(Duration.ofSeconds(309))).isEqualTo("5:09")
        assertThat(formatTimerDuration(Duration.ofSeconds(59))).isEqualTo("0:59")
        assertThat(formatTimerDuration(Duration.ofSeconds(3599))).isEqualTo("59:59")
    }

    @Test fun anHourOrMoreShowsHours() {
        assertThat(formatTimerDuration(Duration.ofSeconds(3600))).isEqualTo("1:00:00")
        assertThat(formatTimerDuration(Duration.ofSeconds(3909))).isEqualTo("1:05:09")
        assertThat(formatTimerDuration(Duration.ofHours(23).plusMinutes(59).plusSeconds(59))).isEqualTo("23:59:59")
    }

    @Test fun zeroAndNegativeClampToZero() {
        assertThat(formatTimerDuration(Duration.ZERO)).isEqualTo("0:00")
        assertThat(formatTimerDuration(Duration.ofSeconds(-5))).isEqualTo("0:00")
    }

    @Test fun fractionsTruncate() {
        assertThat(formatTimerDuration(Duration.ofMillis(5999))).isEqualTo("0:05")
    }
}
