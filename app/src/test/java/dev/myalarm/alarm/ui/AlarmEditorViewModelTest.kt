package dev.myalarm.alarm.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.myalarm.R
import dev.myalarm.alarm.domain.Alarm
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.Recurrence
import dev.myalarm.alarm.scheduling.AlarmRescheduler
import dev.myalarm.alarm.scheduling.SchedulingResult
import dev.myalarm.settings.data.SettingsRepository
import dev.myalarm.testing.FakeAlarmMirror
import dev.myalarm.testing.FakeAlarmRepository
import dev.myalarm.testing.FakeAlarmScheduler
import dev.myalarm.testing.FakeUserUnlockState
import dev.myalarm.testing.FixedTimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmEditorViewModelTest {
    @Before fun setUp() = kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = kotlinx.coroutines.Dispatchers.resetMain()

    @Test fun newAlarmLoadsSettingsDefaults() = runTest {
        val viewModel = viewModel(FakeAlarmRepository(), FakeAlarmScheduler())
        advanceUntilIdle()

        assertThat(viewModel.state.value.snoozeMinutes).isEqualTo(12)
        assertThat(viewModel.state.value.vibration).isFalse()
        assertThat(viewModel.state.value.evidence).isFalse()
    }

    @Test fun existingAlarmPopulatesStateAndEditsAreImmutable() = runTest {
        val existing = alarm(1, "Existing", LocalTime.of(6, 15))
        val viewModel = viewModel(FakeAlarmRepository(listOf(existing)), FakeAlarmScheduler(), 1L)
        advanceUntilIdle()

        assertThat(viewModel.state.value.label).isEqualTo("Existing")
        val before = viewModel.state.value
        viewModel.update { it.copy(label = "Changed") }

        assertThat(before.label).isEqualTo("Existing")
        assertThat(viewModel.state.value.label).isEqualTo("Changed")
    }

    @Test fun savePersistsAndSchedules() = runTest {
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val viewModel = viewModel(repository, scheduler)
        advanceUntilIdle()
        viewModel.update { it.copy(time = LocalTime.of(11, 0), label = "Saved") }
        var completed = false

        viewModel.save { completed = true }
        advanceUntilIdle()

        assertThat(completed).isTrue()
        assertThat(repository.stored.single().label).isEqualTo("Saved")
        assertThat(scheduler.scheduled).containsExactly(AlarmId(1))
    }

    @Test fun schedulingFailureShowsErrorWithoutNavigation() = runTest {
        val repository = FakeAlarmRepository()
        val viewModel = viewModel(repository, FakeAlarmScheduler(SchedulingResult.ExactAlarmsUnavailable))
        advanceUntilIdle()
        var completed = false

        viewModel.save { completed = true }
        advanceUntilIdle()

        assertThat(completed).isFalse()
        assertThat(viewModel.state.value.errorRes).isEqualTo(R.string.alarm_scheduling_exact_unavailable)
        assertThat(repository.stored.single().enabled).isFalse()
    }

    private fun viewModel(repository: FakeAlarmRepository, scheduler: FakeAlarmScheduler, id: Long = -1L): AlarmEditorViewModel =
        AlarmEditorViewModel(
            SavedStateHandle(mapOf("id" to id)),
            repository,
            AlarmRescheduler(repository, scheduler, FixedTimeProvider(), FakeUserUnlockState(), FakeAlarmMirror()),
            SettingsRepository(FakeSettingsDataStore()),
            FixedTimeProvider(),
        )

    private fun alarm(id: Long, label: String, time: LocalTime) = Alarm(
        AlarmId(id), time, true, label, Recurrence.OneTime, null, true, 9, true, Instant.EPOCH, Instant.EPOCH,
    )
}

private class FakeSettingsDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = state

    init {
        state.value = state.value.toMutablePreferences().apply {
            this[intPreferencesKey("default_snooze_minutes")] = 12
            this[booleanPreferencesKey("default_vibration_enabled")] = false
            this[booleanPreferencesKey("evidence_enabled_by_default")] = false
        }.toPreferences()
    }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
