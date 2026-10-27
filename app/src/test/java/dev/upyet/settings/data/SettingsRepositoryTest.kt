package dev.upyet.settings.data

import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.SnoozePolicy
import dev.upyet.testing.FakeDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun defaultIsSixty() = runTest {
        val repository = SettingsRepository(FakeDataStore(emptyPreferences()))

        val lead = repository.settings.first().upcomingAlarmLeadMinutes

        assertThat(lead).isEqualTo(60)
    }

    @Test
    fun timerEvidenceByDefaultIsFalse() = runTest {
        val repository = SettingsRepository(FakeDataStore(emptyPreferences()))

        assertThat(repository.settings.first().timerEvidenceByDefault).isFalse()
    }

    @Test
    fun timerEvidenceByDefaultRoundTrips() = runTest {
        val repository = SettingsRepository(FakeDataStore(emptyPreferences()))

        repository.setTimerEvidenceByDefault(true)
        assertThat(repository.settings.first().timerEvidenceByDefault).isTrue()
        repository.setTimerEvidenceByDefault(false)
        assertThat(repository.settings.first().timerEvidenceByDefault).isFalse()
    }

    @Test
    fun defaultSnoozeIsNineMinutesWhenNothingIsStored() = runTest {
        val repository = SettingsRepository(FakeDataStore(emptyPreferences()))

        assertThat(repository.settings.first().defaultSnooze).isEqualTo(SnoozePolicy.After(9))
    }

    @Test
    fun defaultSnoozeOffRoundTrips() = runTest {
        val repository = SettingsRepository(FakeDataStore(emptyPreferences()))

        repository.setDefaultSnooze(SnoozePolicy.Off)
        assertThat(repository.settings.first().defaultSnooze).isEqualTo(SnoozePolicy.Off)
        repository.setDefaultSnooze(SnoozePolicy.After(15))
        assertThat(repository.settings.first().defaultSnooze).isEqualTo(SnoozePolicy.After(15))
    }

    @Test
    fun roundTripsWrittenValue() = runTest {
        val repository = SettingsRepository(FakeDataStore(emptyPreferences()))

        repository.setUpcomingAlarmLeadMinutes(30)

        assertThat(repository.settings.first().upcomingAlarmLeadMinutes).isEqualTo(30)
        repository.setUpcomingAlarmLeadMinutes(0)
        assertThat(repository.settings.first().upcomingAlarmLeadMinutes).isEqualTo(0)
        repository.setUpcomingAlarmLeadMinutes(120)
        assertThat(repository.settings.first().upcomingAlarmLeadMinutes).isEqualTo(120)
    }
}
