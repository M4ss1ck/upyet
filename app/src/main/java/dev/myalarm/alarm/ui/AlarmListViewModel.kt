package dev.myalarm.alarm.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.myalarm.R
import dev.myalarm.alarm.domain.Alarm
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.AlarmRepository
import dev.myalarm.alarm.domain.NextAlarm
import dev.myalarm.alarm.domain.NextAlarmInfo
import dev.myalarm.alarm.scheduling.AlarmOccurrenceKind
import dev.myalarm.alarm.scheduling.AlarmRescheduler
import dev.myalarm.alarm.scheduling.AlarmScheduler
import dev.myalarm.alarm.scheduling.SchedulingResult
import dev.myalarm.core.time.TimeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class AlarmListUiState(
    val alarms: List<Alarm> = emptyList(),
    @StringRes val errorRes: Int? = null,
    val showReliabilityAction: Boolean = false,
    val nextAlarm: NextAlarmInfo? = null,
    // Refreshed by the ticker so the next-alarm countdown recomposes even when nothing else changed.
    val now: Instant = Instant.EPOCH,
)

private const val TICK_INTERVAL_MS = 30_000L

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val rescheduler: AlarmRescheduler,
    private val scheduler: AlarmScheduler,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val schedulingError = MutableStateFlow<Int?>(null)
    private val failedIds = MutableStateFlow<Set<AlarmId>>(emptySet())

    // Ticks while the state flow has a subscriber and stops with it, so the countdown stays live
    // without an idle screen polling in the background.
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(TICK_INTERVAL_MS)
        }
    }

    val state: StateFlow<AlarmListUiState> = combine(
        repository.observeAlarms(),
        schedulingError,
        failedIds,
        ticker,
    ) { alarms, error, failed, _ ->
        val now = timeProvider.now()
        val visibleAlarms = alarms.map { if (it.id in failed) it.copy(enabled = false) else it }
        AlarmListUiState(
            alarms = visibleAlarms,
            errorRes = error,
            showReliabilityAction = error != null,
            nextAlarm = NextAlarm.select(visibleAlarms, timeProvider.zone(), now),
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmListUiState())

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        if (enabled) {
            when (val result = rescheduler.scheduleNext(alarm.copy(enabled = true))) {
                SchedulingResult.Scheduled -> {
                    failedIds.value -= alarm.id
                    schedulingError.value = null
                    repository.setEnabled(alarm.id, true)
                }

                SchedulingResult.ExactAlarmsUnavailable -> schedulingFailed(alarm.id, R.string.alarm_scheduling_exact_unavailable)

                is SchedulingResult.Failed -> schedulingFailed(alarm.id, R.string.alarm_scheduling_failed)
            }
        } else {
            scheduler.cancel(alarm.id, AlarmOccurrenceKind.MAIN)
            scheduler.cancel(alarm.id, AlarmOccurrenceKind.SNOOZE)
            failedIds.value -= alarm.id
            repository.setEnabled(alarm.id, false)
        }
    }

    private suspend fun schedulingFailed(id: AlarmId, @StringRes message: Int) {
        failedIds.value += id
        schedulingError.value = message
        repository.setEnabled(id, false)
    }

    fun delete(id: AlarmId) = viewModelScope.launch {
        scheduler.cancel(id, AlarmOccurrenceKind.MAIN)
        scheduler.cancel(id, AlarmOccurrenceKind.SNOOZE)
        repository.delete(id)
    }
}
