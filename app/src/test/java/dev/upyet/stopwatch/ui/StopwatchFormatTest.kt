package dev.upyet.stopwatch.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.util.Locale

class StopwatchFormatTest {
    private fun format(d: Duration) = formatStopwatch(d, Locale.ROOT)

    @Test fun zeroIsAllZeros() {
        assertThat(format(Duration.ZERO)).isEqualTo("00:00.00")
    }

    @Test fun underAMinuteShowsMinutesSecondsAndHundredths() {
        assertThat(format(Duration.ofMillis(61_234))).isEqualTo("01:01.23")
    }

    @Test fun hundredthsAreTruncatedNotRounded() {
        // 19 ms is 1.9 hundredths: rounding would say .02, a stopwatch shows the time that had passed.
        assertThat(format(Duration.ofMillis(19))).isEqualTo("00:00.01")
        assertThat(format(Duration.ofMillis(9))).isEqualTo("00:00.00")
    }

    @Test fun justBelowAnHourStaysInTheMinutesLayout() {
        assertThat(format(Duration.ofMillis(3_599_990))).isEqualTo("59:59.99")
    }

    @Test fun anHourOrMoreAddsTheHourField() {
        assertThat(format(Duration.ofMillis(3_600_000))).isEqualTo("1:00:00.00")
        assertThat(format(Duration.ofMillis(3_723_500))).isEqualTo("1:02:03.50")
        assertThat(format(Duration.ofHours(10))).isEqualTo("10:00:00.00")
    }

    @Test fun negativeClampsToZero() {
        assertThat(format(Duration.ofSeconds(-5))).isEqualTo("00:00.00")
    }

    @Test fun digitsFollowTheLocale() {
        assertThat(formatStopwatch(Duration.ofMillis(5_090), Locale.forLanguageTag("ar-EG")))
            .isEqualTo("٠٠:٠٥.٠٩")
    }
}
