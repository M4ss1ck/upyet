package dev.myalarm.alarm.ui

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.myalarm.R
import dev.myalarm.alarm.domain.Alarm
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.AlarmRepository
import dev.myalarm.alarm.domain.Recurrence
import dev.myalarm.alarm.scheduling.AlarmRescheduler
import dev.myalarm.alarm.scheduling.SchedulingResult
import dev.myalarm.settings.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

data class AlarmEditorUiState(
    val time: LocalTime = LocalTime.of(7, 0),
    val label: String = "",
    val recurrence: Recurrence = Recurrence.OneTime,
    val vibration: Boolean = true,
    val snoozeMinutes: Int = 9,
    val evidence: Boolean = true,
    val soundUri: String? = null,
    @StringRes val errorRes: Int? = null,
    val showReliabilityAction: Boolean = false,
)

@HiltViewModel
class AlarmEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: AlarmRepository,
    private val rescheduler: AlarmRescheduler,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(savedState["editor"] ?: AlarmEditorUiState())
    val state: StateFlow<AlarmEditorUiState> = _state.asStateFlow()
    private val alarmId: Long? = savedState.get<Long>("id")?.takeIf { it >= 0 }

    init {
        viewModelScope.launch {
            val existing = alarmId?.let { repository.getAlarm(AlarmId(it)) }
            if (existing != null) {
                updateFromAlarm(existing)
            } else if (savedState.get<AlarmEditorUiState>("editor") == null) {
                val defaults = settingsRepository.settings.first()
                update {
                    it.copy(
                        vibration = defaults.defaultVibrationEnabled,
                        snoozeMinutes = defaults.defaultSnoozeMinutes,
                        evidence = defaults.evidenceEnabledByDefault,
                    )
                }
            }
        }
    }

    fun update(transform: (AlarmEditorUiState) -> AlarmEditorUiState) {
        _state.value = transform(_state.value)
        savedState["editor"] = _state.value
    }

    private fun updateFromAlarm(alarm: Alarm) {
        _state.value =
            AlarmEditorUiState(
                alarm.time,
                alarm.label,
                alarm.recurrence,
                alarm.vibrationEnabled,
                alarm.snoozeMinutes,
                alarm.evidenceEnabled,
                alarm.soundUri,
            )
        savedState["editor"] = _state.value
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val now = Instant.now()
        val existing = alarmId?.let { repository.getAlarm(AlarmId(it)) }
        val current = _state.value
        val alarm = Alarm(
            id = AlarmId(alarmId ?: 0),
            time = current.time,
            enabled = true,
            label = current.label,
            recurrence = current.recurrence,
            soundUri = current.soundUri,
            vibrationEnabled = current.vibration,
            snoozeMinutes = current.snoozeMinutes,
            evidenceEnabled = current.evidence,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val id = repository.upsert(alarm)
        when (val result = rescheduler.scheduleNext(alarm.copy(id = id))) {
            SchedulingResult.Scheduled -> onDone()

            SchedulingResult.ExactAlarmsUnavailable -> {
                repository.setEnabled(id, false)
                update {
                    it.copy(
                        errorRes = R.string.alarm_scheduling_exact_unavailable,
                        showReliabilityAction = true,
                    )
                }
            }

            is SchedulingResult.Failed -> {
                repository.setEnabled(id, false)
                update {
                    it.copy(
                        errorRes = R.string.alarm_scheduling_failed,
                        showReliabilityAction = true,
                    )
                }
            }
        }
    }
}
