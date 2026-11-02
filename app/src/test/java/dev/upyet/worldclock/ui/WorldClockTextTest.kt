package dev.upyet.worldclock.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalTime

class WorldClockTextTest {
    @Test
    fun aTwentyFourHourFaceReadsTheHourAsItIs() {
        assertThat(displayedHour(LocalTime.of(1, 0), is24Hour = true)).isEqualTo(1)
        assertThat(displayedHour(LocalTime.of(13, 0), is24Hour = true)).isEqualTo(13)
        assertThat(displayedHour(LocalTime.MIDNIGHT, is24Hour = true)).isEqualTo(0)
    }

    @Test
    fun aTwelveHourFaceFoldsTheAfternoonAndReadsMidnightAndNoonAsTwelve() {
        assertThat(displayedHour(LocalTime.of(1, 0), is24Hour = false)).isEqualTo(1)
        assertThat(displayedHour(LocalTime.of(13, 0), is24Hour = false)).isEqualTo(1)
        assertThat(displayedHour(LocalTime.MIDNIGHT, is24Hour = false)).isEqualTo(12)
        assertThat(displayedHour(LocalTime.NOON, is24Hour = false)).isEqualTo(12)
    }
}
