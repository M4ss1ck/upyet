package dev.upyet.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LaunchDestinationTest {
    private fun roundTrip(destination: LaunchDestination): LaunchDestination? {
        val extras = destination.toExtras()
        return decodeLaunchDestination(extras[EXTRA_DESTINATION], extras[EXTRA_ALARM_PREFILL])
    }

    @Test fun everyDestinationRoundTrips() {
        listOf(
            LaunchDestination.Alarms,
            LaunchDestination.Timers,
            LaunchDestination.Reliability,
            LaunchDestination.AlarmEditor(null),
            LaunchDestination.AlarmEditor("abc_-123"),
        ).forEach { assertThat(roundTrip(it)).isEqualTo(it) }
    }

    @Test fun onlyTheEditorCarriesAPrefill() {
        assertThat(LaunchDestination.Alarms.toExtras().keys).containsExactly(EXTRA_DESTINATION)
        assertThat(LaunchDestination.AlarmEditor("t").toExtras()).containsEntry(EXTRA_ALARM_PREFILL, "t")
    }

    @Test fun wireNamesAreStable() {
        assertThat(LaunchDestination.Alarms.toExtras()[EXTRA_DESTINATION]).isEqualTo("alarms")
        assertThat(LaunchDestination.Timers.toExtras()[EXTRA_DESTINATION]).isEqualTo("timers")
        assertThat(LaunchDestination.Reliability.toExtras()[EXTRA_DESTINATION]).isEqualTo("reliability")
        assertThat(LaunchDestination.AlarmEditor(null).toExtras()[EXTRA_DESTINATION]).isEqualTo("alarm_editor")
    }

    @Test fun unknownOrMissingNameIsNull() {
        assertThat(decodeLaunchDestination(null, null)).isNull()
        assertThat(decodeLaunchDestination("nope", "x")).isNull()
        assertThat(decodeLaunchDestination("", null)).isNull()
    }
}
