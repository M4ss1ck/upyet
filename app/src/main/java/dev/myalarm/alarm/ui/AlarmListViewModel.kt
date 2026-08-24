package dev.myalarm.alarm.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.myalarm.R
import dev.myalarm.alarm.domain.Alarm
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.AlarmRepository
import dev.myalarm.alarm.scheduling.AlarmOccurrenceKind
import dev.myalarm.alarm.scheduling.AlarmRescheduler
import dev.myalarm.alarm.scheduling.AlarmScheduler
import dev.myalarm.alarm.scheduling.SchedulingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlarmListUiState(
    val alarms: List<Alarm> = emptyList(),
    @StringRes val errorRes: Int? = null,
    val showReliabilityAction: Boolean = false,
)

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val rescheduler: AlarmRescheduler,
    private val scheduler: AlarmScheduler,
) : ViewModel() {
    private val schedulingError = MutableStateFlow<Int?>(null)
    private val failedIds = MutableStateFlow<Set<AlarmId>>(emptySet())
    val state: StateFlow<AlarmListUiState> = combine(repository.observeAlarms(), schedulingError, failedIds) { alarms, error, failed ->
        AlarmListUiState(alarms.map { if (it.id in failed) it.copy(enabled = false) else it }, error, error != null)
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
