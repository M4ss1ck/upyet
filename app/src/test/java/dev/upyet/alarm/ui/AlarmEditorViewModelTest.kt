package dev.upyet.alarm.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.alarm.scheduling.AlarmRescheduler
import dev.upyet.alarm.scheduling.SchedulingResult
import dev.upyet.settings.data.SettingsRepository
import dev.upyet.testing.FakeAlarmMirror
import dev.upyet.testing.FakeAlarmRepository
import dev.upyet.testing.FakeAlarmScheduler
import dev.upyet.testing.FakeDataStore
import dev.upyet.testing.FakeUpcomingAlarmScheduler
import dev.upyet.testing.FakeUserUnlockState
import dev.upyet.testing.FixedTimeProvider
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

    @Test fun newAlarmDefaultsToNextFiveMinuteMark() = runTest {
        val viewModel = viewModel(FakeAlarmRepository(), FakeAlarmScheduler(), now = "2026-08-24T13:02:00Z")
        advanceUntilIdle()

        assertThat(viewModel.state.value.time).isEqualTo(LocalTime.of(13, 5))
        assertThat(viewModel.state.value.isLoaded).isTrue()
    }

    @Test fun newAlarmSkipsAMarkThatIsLessThanTwoMinutesAway() = runTest {
        val viewModel = viewModel(FakeAlarmRepository(), FakeAlarmScheduler(), now = "2026-08-24T13:04:45Z")
        advanceUntilIdle()

        assertThat(viewModel.state.value.time).isEqualTo(LocalTime.of(13, 10))
    }

    @Test fun newAlarmDefaultWrapsPastMidnight() = runTest {
        val viewModel = viewModel(FakeAlarmRepository(), FakeAlarmScheduler(), now = "2026-08-24T23:58:30Z")
        advanceUntilIdle()

        assertThat(viewModel.state.value.time).isEqualTo(LocalTime.of(0, 5))
    }

    @Test fun existingAlarmKeepsItsStoredTime() = runTest {
        val existing = alarm(1, "Existing", LocalTime.of(6, 15))
        val viewModel = viewModel(FakeAlarmRepository(listOf(existing)), FakeAlarmScheduler(), 1L)
        advanceUntilIdle()

        assertThat(viewModel.state.value.time).isEqualTo(LocalTime.of(6, 15))
    }

    @Test fun restoredDraftTimeWinsOverTheDefault() = runTest {
        val savedState = SavedStateHandle(
            mapOf("id" to -1L, "draft" to true, "draft_minute_of_day" to 6 * 60 + 15),
        )
        val viewModel = viewModel(FakeAlarmRepository(), FakeAlarmScheduler(), savedState = savedState)
        advanceUntilIdle()

        assertThat(viewModel.state.value.time).isEqualTo(LocalTime.of(6, 15))
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

    private fun viewModel(
        repository: FakeAlarmRepository,
        scheduler: FakeAlarmScheduler,
        id: Long = -1L,
        now: String = "2026-08-24T10:00:00Z",
        savedState: SavedStateHandle = SavedStateHandle(mapOf("id" to id)),
    ): AlarmEditorViewModel = AlarmEditorViewModel(
        savedState,
        repository,
        AlarmRescheduler(
            repository,
            scheduler,
            FixedTimeProvider(),
            FakeUserUnlockState(),
            FakeAlarmMirror(),
            SettingsRepository(FakeDataStore()),
            FakeUpcomingAlarmScheduler(),
        ),
        SettingsRepository(FakeSettingsDataStore()),
        FixedTimeProvider(Instant.parse(now)),
    )

    private fun alarm(id: Long, label: String, time: LocalTime) = Alarm(
        AlarmId(id),
        time,
        true,
        label,
        Recurrence.OneTime,
        null,
        true,
        true,
        9,
        true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
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
