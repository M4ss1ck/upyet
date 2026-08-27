package dev.upyet.settings.data

import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
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
