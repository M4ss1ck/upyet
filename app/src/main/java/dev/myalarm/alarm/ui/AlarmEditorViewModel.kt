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
import dev.myalarm.core.directboot.MirroredRecurrence
import dev.myalarm.core.time.TimeProvider
import dev.myalarm.settings.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

private const val MINUTES_PER_HOUR = 60
private const val DEFAULT_SNOOZE_MINUTES = 9
private const val DEFAULT_HOUR = 7

private const val KEY_ALARM_ID = "id"
private const val KEY_DRAFT = "draft"
private const val KEY_MINUTE_OF_DAY = "draft_minute_of_day"
private const val KEY_LABEL = "draft_label"
private const val KEY_RECURRENCE_TYPE = "draft_recurrence_type"
private const val KEY_WEEKDAY_MASK = "draft_weekday_mask"
private const val KEY_VIBRATION = "draft_vibration"
private const val KEY_SNOOZE_MINUTES = "draft_snooze_minutes"
private const val KEY_EVIDENCE = "draft_evidence"
private const val KEY_SOUND_URI = "draft_sound_uri"

data class AlarmEditorUiState(
    val time: LocalTime = LocalTime.of(DEFAULT_HOUR, 0),
    val label: String = "",
    val recurrence: Recurrence = Recurrence.OneTime,
    val vibration: Boolean = true,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val evidence: Boolean = true,
    val soundUri: String? = null,
    /** False until the stored alarm (or the defaults) have been read; the time picker must wait for it. */
    val isLoaded: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val showReliabilityAction: Boolean = false,
)

@HiltViewModel
class AlarmEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: AlarmRepository,
    private val rescheduler: AlarmRescheduler,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(restoreDraft() ?: AlarmEditorUiState())
    val state: StateFlow<AlarmEditorUiState> = _state.asStateFlow()
    private val alarmId: Long? = savedState.get<Long>(KEY_ALARM_ID)?.takeIf { it >= 0 }

    init {
        viewModelScope.launch {
            val existing = alarmId?.let { repository.getAlarm(AlarmId(it)) }
            if (hasDraft()) {
                // A draft survived process recreation; it wins over both the stored alarm and the defaults.
                update { it.copy(isLoaded = true) }
            } else if (existing != null) {
                updateFromAlarm(existing)
            } else {
                val defaults = settingsRepository.settings.first()
                update {
                    it.copy(
                        vibration = defaults.defaultVibrationEnabled,
                        snoozeMinutes = defaults.defaultSnoozeMinutes,
                        evidence = defaults.evidenceEnabledByDefault,
                        isLoaded = true,
                    )
                }
            }
        }
    }

    fun update(transform: (AlarmEditorUiState) -> AlarmEditorUiState) {
        _state.value = transform(_state.value)
        saveDraft(_state.value)
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
                isLoaded = true,
            )
        saveDraft(_state.value)
    }

    /**
     * SavedStateHandle only accepts Bundle-compatible values, so the draft is stored as primitives rather
     * than as the UI state object. This is what survives process death while the editor is open.
     */
    private fun saveDraft(state: AlarmEditorUiState) {
        val (recurrenceType, weekdayMask) = MirroredRecurrence.encode(state.recurrence)
        savedState[KEY_DRAFT] = true
        savedState[KEY_MINUTE_OF_DAY] = state.time.hour * MINUTES_PER_HOUR + state.time.minute
        savedState[KEY_LABEL] = state.label
        savedState[KEY_RECURRENCE_TYPE] = recurrenceType
        savedState[KEY_WEEKDAY_MASK] = weekdayMask
        savedState[KEY_VIBRATION] = state.vibration
        savedState[KEY_SNOOZE_MINUTES] = state.snoozeMinutes
        savedState[KEY_EVIDENCE] = state.evidence
        savedState[KEY_SOUND_URI] = state.soundUri
    }

    private fun hasDraft(): Boolean = savedState.get<Boolean>(KEY_DRAFT) == true

    private fun restoreDraft(): AlarmEditorUiState? {
        if (!hasDraft()) return null
        val minuteOfDay = savedState.get<Int>(KEY_MINUTE_OF_DAY) ?: return null
        return AlarmEditorUiState(
            time = LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR),
            label = savedState.get<String>(KEY_LABEL).orEmpty(),
            recurrence =
            MirroredRecurrence.decode(
                savedState.get<String>(KEY_RECURRENCE_TYPE) ?: MirroredRecurrence.ONE_TIME,
                savedState.get<Int>(KEY_WEEKDAY_MASK) ?: 0,
            ),
            vibration = savedState.get<Boolean>(KEY_VIBRATION) ?: true,
            snoozeMinutes = savedState.get<Int>(KEY_SNOOZE_MINUTES) ?: DEFAULT_SNOOZE_MINUTES,
            evidence = savedState.get<Boolean>(KEY_EVIDENCE) ?: true,
            soundUri = savedState.get<String>(KEY_SOUND_URI),
        )
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val now = timeProvider.now()
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
