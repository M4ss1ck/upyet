package dev.upyet.alarm.ui

import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import dev.upyet.alarm.scheduling.AlarmRescheduler
import dev.upyet.alarm.scheduling.SchedulingResult
import dev.upyet.alarm.scheduling.SkipNextOccurrence
import dev.upyet.settings.data.SettingsRepository
import dev.upyet.testing.FakeAlarmMirror
import dev.upyet.testing.FakeAlarmRepository
import dev.upyet.testing.FakeAlarmScheduler
import dev.upyet.testing.FakeDataStore
import dev.upyet.testing.FakeUpcomingAlarmScheduler
import dev.upyet.testing.FakeUserUnlockState
import dev.upyet.testing.FixedTimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmListViewModelTest {
    @Before fun setUp() = kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = kotlinx.coroutines.Dispatchers.resetMain()

    @Test fun exactAlarmFailureRollsBackEnabledStateAndExposesError() = runTest {
        val alarm = alarm(enabled = false)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler(SchedulingResult.ExactAlarmsUnavailable)
        val viewModel = viewModel(repository, scheduler, FixedTimeProvider())
        val observer = launch { viewModel.state.collect {} }

        viewModel.setEnabled(alarm, true)
        runCurrent()

        assertThat(viewModel.state.value.alarms.single().enabled).isFalse()
        assertThat(viewModel.state.value.errorRes).isEqualTo(R.string.alarm_scheduling_exact_unavailable)
        observer.cancel()
    }

    @Test fun successfulEnableClearsPreviousError() = runTest {
        val alarm = alarm(enabled = false)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler(SchedulingResult.ExactAlarmsUnavailable)
        val viewModel = viewModel(repository, scheduler, FixedTimeProvider())
        val observer = launch { viewModel.state.collect {} }
        viewModel.setEnabled(alarm, true)
        runCurrent()

        scheduler.result = SchedulingResult.Scheduled
        viewModel.setEnabled(alarm, true)
        runCurrent()

        assertThat(viewModel.state.value.errorRes).isNull()
        assertThat(viewModel.state.value.alarms.single().enabled).isTrue()
        observer.cancel()
    }

    @Test fun deletingCancelsBothKindsAndOnlyDeletesAlarm() = runTest {
        val alarm = alarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = viewModel(repository, scheduler, FixedTimeProvider())
        val observer = launch { viewModel.state.collect {} }

        viewModel.delete(alarm.id)
        runCurrent()

        assertThat(scheduler.cancelled).containsExactly(
            alarm.id to AlarmOccurrenceKind.MAIN,
            alarm.id to AlarmOccurrenceKind.SNOOZE,
        )
        assertThat(repository.deleted).containsExactly(alarm.id)
        observer.cancel()
    }

    @Test fun stateExposesNextAlarmForTheSoonestEnabledAlarm() = runTest {
        val alarm = alarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val timeProvider = FixedTimeProvider()
        val viewModel = viewModel(repository, scheduler, timeProvider)
        val observer = launch { viewModel.state.collect {} }
        runCurrent()

        assertThat(viewModel.state.value.nextAlarm?.time).isEqualTo(alarm.time)
        assertThat(viewModel.state.value.nextAlarm?.label).isEqualTo(alarm.label)
        observer.cancel()
    }

    @Test fun toggleSkipNextOnDailyAlarmPersistsTodayAndReschedules() = runTest {
        val timeProvider = FixedTimeProvider()
        val alarm = dailyAlarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = viewModel(repository, scheduler, timeProvider)

        viewModel.toggleSkipNext(alarm)
        runCurrent()

        val expectedDate = LocalDate.of(2026, 8, 24)
        assertThat(repository.stored.single().skipNextOn).isEqualTo(expectedDate)
        assertThat(scheduler.scheduled).containsExactly(alarm.id)
    }

    @Test fun toggleSkipNextAgainClearsIt() = runTest {
        val timeProvider = FixedTimeProvider()
        val skipDate = LocalDate.of(2026, 8, 24)
        val alarm = dailyAlarm(enabled = true).copy(skipNextOn = skipDate)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = viewModel(repository, scheduler, timeProvider)

        viewModel.toggleSkipNext(alarm)
        runCurrent()

        assertThat(repository.stored.single().skipNextOn).isNull()
        assertThat(scheduler.scheduled).containsExactly(alarm.id)
    }

    @Test fun toggleSkipNextIsNoOpForOneTimeAlarm() = runTest {
        val timeProvider = FixedTimeProvider()
        val alarm = alarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = viewModel(repository, scheduler, timeProvider)

        viewModel.toggleSkipNext(alarm)
        runCurrent()

        assertThat(repository.stored.single().skipNextOn).isNull()
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test fun toggleSkipNextIsNoOpForDisabledAlarm() = runTest {
        val timeProvider = FixedTimeProvider()
        val alarm = dailyAlarm(enabled = false)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = viewModel(repository, scheduler, timeProvider)

        viewModel.toggleSkipNext(alarm)
        runCurrent()

        assertThat(repository.stored.single().skipNextOn).isNull()
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test fun stateListsOnlySkipsThatHaveNotLapsed() = runTest {
        val today = LocalDate.of(2026, 8, 24)
        val lapsed = dailyAlarm(enabled = true).copy(id = AlarmId(1), time = LocalTime.of(7, 0), skipNextOn = today)
        val active = dailyAlarm(enabled = true).copy(id = AlarmId(2), time = LocalTime.of(11, 0), skipNextOn = today)
        val viewModel = viewModel(FakeAlarmRepository(listOf(lapsed, active)), FakeAlarmScheduler(), FixedTimeProvider())
        val observer = launch { viewModel.state.collect {} }
        runCurrent()

        assertThat(viewModel.state.value.activeSkips).containsExactly(AlarmId(2), today)
        observer.cancel()
    }

    @Test fun turningOffARecurringAlarmAsksFirstAndChangesNothing() = runTest {
        val alarm = dailyAlarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = viewModel(repository, scheduler, FixedTimeProvider())
        val observer = launch { viewModel.state.collect {} }

        viewModel.setEnabled(alarm, false)
        runCurrent()

        assertThat(viewModel.state.value.turnOffPrompt).isEqualTo(TurnOffPrompt(alarm.id, alarm.time, LocalDate.of(2026, 8, 24)))
        assertThat(repository.enabledChanges).isEmpty()
        assertThat(scheduler.cancelled).isEmpty()
        observer.cancel()
    }

    @Test fun promptAfterALapsedSkipOffersTheFollowingDay() = runTest {
        val alarm = dailyAlarm(enabled = true).copy(time = LocalTime.of(7, 0), skipNextOn = LocalDate.of(2026, 8, 24))
        val viewModel = viewModel(FakeAlarmRepository(listOf(alarm)), FakeAlarmScheduler(), FixedTimeProvider())
        val observer = launch { viewModel.state.collect {} }

        viewModel.setEnabled(alarm, false)
        runCurrent()

        assertThat(viewModel.state.value.turnOffPrompt?.skipDate).isEqualTo(LocalDate.of(2026, 8, 25))
        observer.cancel()
    }

    @Test fun turningOffAOneTimeAlarmDoesNotAsk() = runTest {
        val alarm = alarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val viewModel = viewModel(repository, FakeAlarmScheduler(), FixedTimeProvider())
        val observer = launch { viewModel.state.collect {} }

        viewModel.setEnabled(alarm, false)
        runCurrent()

        assertThat(viewModel.state.value.turnOffPrompt).isNull()
        assertThat(repository.stored.single().enabled).isFalse()
        observer.cancel()
    }

    @Test fun turningOffAnAlreadySkippedAlarmDoesNotAskAndDropsTheSkip() = runTest {
        val alarm = dailyAlarm(enabled = true).copy(skipNextOn = LocalDate.of(2026, 8, 24))
        val repository = FakeAlarmRepository(listOf(alarm))
        val viewModel = viewModel(repository, FakeAlarmScheduler(), FixedTimeProvider())
        val observer = launch { viewModel.state.collect {} }

        viewModel.setEnabled(alarm, false)
        runCurrent()

        assertThat(viewModel.state.value.turnOffPrompt).isNull()
        assertThat(repository.stored.single().enabled).isFalse()
        assertThat(repository.stored.single().skipNextOn).isNull()
        observer.cancel()
    }

    @Test fun confirmingTurnOffDisablesAndCancelsBothKinds() = runTest {
        val alarm = dailyAlarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = viewModel(repository, scheduler, FixedTimeProvider())
        val observer = launch { viewModel.state.collect {} }
        viewModel.setEnabled(alarm, false)
        runCurrent()

        viewModel.confirmTurnOff()
        runCurrent()

        assertThat(viewModel.state.value.turnOffPrompt).isNull()
        assertThat(repository.stored.single().enabled).isFalse()
        assertThat(scheduler.cancelled).containsExactly(
            alarm.id to AlarmOccurrenceKind.MAIN,
            alarm.id to AlarmOccurrenceKind.SNOOZE,
        )
        observer.cancel()
    }

    @Test fun choosingSkipKeepsTheAlarmOnSkipsTheNextAndCancelsTheSnooze() = runTest {
        val alarm = dailyAlarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = viewModel(repository, scheduler, FixedTimeProvider())
        val observer = launch { viewModel.state.collect {} }
        viewModel.setEnabled(alarm, false)
        runCurrent()

        viewModel.skipInsteadOfTurnOff()
        runCurrent()

        val stored = repository.stored.single()
        assertThat(viewModel.state.value.turnOffPrompt).isNull()
        assertThat(stored.enabled).isTrue()
        assertThat(stored.skipNextOn).isEqualTo(LocalDate.of(2026, 8, 24))
        assertThat(scheduler.cancelled).contains(alarm.id to AlarmOccurrenceKind.SNOOZE)
        assertThat(scheduler.scheduled).containsExactly(alarm.id)
        observer.cancel()
    }

    @Test fun dismissingThePromptChangesNothing() = runTest {
        val alarm = dailyAlarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = viewModel(repository, scheduler, FixedTimeProvider())
        val observer = launch { viewModel.state.collect {} }
        viewModel.setEnabled(alarm, false)
        runCurrent()

        viewModel.dismissTurnOffPrompt()
        runCurrent()

        assertThat(viewModel.state.value.turnOffPrompt).isNull()
        assertThat(repository.stored.single()).isEqualTo(alarm)
        assertThat(scheduler.cancelled).isEmpty()
        assertThat(scheduler.scheduled).isEmpty()
        observer.cancel()
    }

    /** A skip arriving while the prompt is open (from the notification) must not make the dialog vanish and later return. */
    @Test fun promptStaysOpenWhenASkipArrivesMeanwhile() = runTest {
        val alarm = dailyAlarm(enabled = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val viewModel = viewModel(repository, FakeAlarmScheduler(), FixedTimeProvider())
        val observer = launch { viewModel.state.collect {} }
        viewModel.setEnabled(alarm, false)
        runCurrent()

        repository.setSkipNextOn(alarm.id, LocalDate.of(2026, 8, 24))
        runCurrent()

        assertThat(viewModel.state.value.turnOffPrompt?.skipDate).isEqualTo(LocalDate.of(2026, 8, 24))
        observer.cancel()
    }

    private fun viewModel(
        repository: FakeAlarmRepository,
        scheduler: FakeAlarmScheduler,
        timeProvider: FixedTimeProvider,
    ): AlarmListViewModel {
        val rescheduler = rescheduler(repository, scheduler, timeProvider)
        val skip = SkipNextOccurrence(repository, timeProvider, rescheduler)
        return AlarmListViewModel(repository, rescheduler, scheduler, timeProvider, skip)
    }

    private fun rescheduler(
        repository: FakeAlarmRepository,
        scheduler: FakeAlarmScheduler,
        timeProvider: FixedTimeProvider = FixedTimeProvider(),
    ) = AlarmRescheduler(
        repository,
        scheduler,
        timeProvider,
        FakeUserUnlockState(),
        FakeAlarmMirror(),
        settings(),
        FakeUpcomingAlarmScheduler(),
    )

    private fun settings() = SettingsRepository(FakeDataStore())

    private fun alarm(enabled: Boolean) = Alarm(
        AlarmId(1),
        LocalTime.of(11, 0),
        enabled,
        "Wake up",
        Recurrence.OneTime,
        null,
        true,
        true,
        9,
        false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun dailyAlarm(enabled: Boolean) = Alarm(
        AlarmId(1),
        LocalTime.of(11, 0),
        enabled,
        "Wake up",
        Recurrence.Daily,
        null,
        true,
        true,
        9,
        false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
