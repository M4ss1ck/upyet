package dev.upyet.timer.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.core.time.TimerClock
import dev.upyet.settings.data.SettingsRepository
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRepository
import dev.upyet.timer.domain.TimerRunState
import dev.upyet.timer.domain.TimerTransition
import dev.upyet.timer.ringing.TimerRingingCommands
import dev.upyet.timer.scheduling.TimerEditor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

private const val KEY_HOURS = "h"
private const val KEY_MINUTES = "m"
private const val KEY_SECONDS = "s"
private const val KEY_LABEL = "label"

/** Absent means "follow the global default"; present means the user flipped the switch for this new timer. */
private const val KEY_RECORD = "record"

/** Only a display refresh for the remaining time. It never schedules anything: the exact alarm is TimerScheduler's. */
private const val DISPLAY_TICK_MS = 1_000L

private const val MAX_HOURS = 23
private const val MAX_MINUTES_OR_SECONDS = 59

/**
 * The Timer tab (AGENTS.md §13). The wheel values, the label and the new timer's evidence switch live in
 * [SavedStateHandle] so they survive process death; every timer edit goes through [TimerEditor], which takes
 * the same lock as expiry and discovery. Ringing rows are the exception: Stop and +1 min belong to
 * [TimerRingingCommands], because the ringing service owns the ringing (AGENTS.md §2 inv. 2).
 */
@HiltViewModel
class TimerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: TimerRepository,
    private val editor: TimerEditor,
    private val settings: SettingsRepository,
    private val clock: TimerClock,
    private val ringing: TimerRingingCommands,
) : ViewModel() {
    private val hours = savedStateHandle.getStateFlow(KEY_HOURS, 0)
    private val minutes = savedStateHandle.getStateFlow(KEY_MINUTES, 0)
    private val seconds = savedStateHandle.getStateFlow(KEY_SECONDS, 0)
    private val label = savedStateHandle.getStateFlow(KEY_LABEL, "")
    private val recordOverride = savedStateHandle.getStateFlow<Boolean?>(KEY_RECORD, null)

    private val draft: Flow<Draft> =
        combine(hours, minutes, seconds, label, recordOverride) { h, m, s, l, record ->
            Draft(h, m, s, l, record)
        }

    /**
     * Re-emits the rows once a second only while a timer runs, so the countdowns move. A list with nothing running
     * emits once and stays quiet. The ticker is a display refresh and nothing else (AGENTS.md §6a).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rows: Flow<List<TimerRowState>> =
        repository.observeTimers().flatMapLatest { timers ->
            if (timers.any { it.runState is TimerRunState.Running }) {
                flow {
                    while (true) {
                        emit(timerRows(timers, clock.now()))
                        delay(DISPLAY_TICK_MS)
                    }
                }
            } else {
                flow { emit(timerRows(timers, clock.now())) }
            }
        }

    val state: StateFlow<TimerUiState> =
        combine(draft, settings.settings, rows) { draft, saved, rows ->
            TimerUiState(
                hours = draft.hours,
                minutes = draft.minutes,
                seconds = draft.seconds,
                label = draft.label,
                recordNewTimer = draft.recordOverride ?: saved.timerEvidenceByDefault,
                timers = rows,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TimerUiState())

    fun onHours(value: Int) = savedStateHandle.set(KEY_HOURS, value.coerceIn(0, MAX_HOURS))

    fun onMinutes(value: Int) = savedStateHandle.set(KEY_MINUTES, value.coerceIn(0, MAX_MINUTES_OR_SECONDS))

    fun onSeconds(value: Int) = savedStateHandle.set(KEY_SECONDS, value.coerceIn(0, MAX_MINUTES_OR_SECONDS))

    /** A preset sets the wheels to whole minutes, so 90 becomes 1 h 30 m 0 s. */
    fun onPreset(minutes: Int) {
        savedStateHandle[KEY_HOURS] = (minutes / MINUTES_PER_HOUR).coerceIn(0, MAX_HOURS)
        savedStateHandle[KEY_MINUTES] = minutes % MINUTES_PER_HOUR
        savedStateHandle[KEY_SECONDS] = 0
    }

    fun onLabel(value: String) = savedStateHandle.set(KEY_LABEL, value)

    fun onRecordNewTimer(enabled: Boolean) = savedStateHandle.set(KEY_RECORD, enabled)

    /**
     * Creates and starts a timer of the wheel length. The global default is read only here, at creation, and only
     * when the user has not flipped the switch (AGENTS.md §2 inv. 12): a later change to the default never reaches
     * a timer that already exists.
     */
    fun onStart() {
        val length = draftLength()
        if (length.isZero) return
        viewModelScope.launch {
            val now = clock.now()
            val evidence = recordOverride.value ?: settings.settings.first().timerEvidenceByDefault
            val created = Timer.create(
                id = TimerId(0),
                length = length,
                label = label.value.trim(),
                ringtoneUri = null,
                evidenceEnabled = evidence,
                createdAt = now.wallClock,
            )
            val started = created.start(now) as? TimerTransition.Applied ?: return@launch
            editor.add(started.timer)
            savedStateHandle[KEY_LABEL] = ""
            savedStateHandle[KEY_RECORD] = null
        }
    }

    fun onStartTimer(id: TimerId) = edit(id, "start") { timer, now -> timer.start(now) }

    fun onResume(id: TimerId) = edit(id, "start") { timer, now -> timer.start(now) }

    fun onPause(id: TimerId) = edit(id, "pause") { timer, now -> timer.pause(now) }

    fun onReset(id: TimerId) = edit(id, "reset") { timer, _ -> timer.reset() }

    fun onDelete(id: TimerId) = edit(id, "delete") { timer, _ -> timer.delete() }

    /**
     * A ringing timer's +1 min is the ringing service's command: [TimerEditor] refuses every edit to a Ringing timer.
     */
    fun onAddMinute(id: TimerId) {
        viewModelScope.launch {
            if (repository.getTimer(id)?.runState == TimerRunState.Ringing) {
                ringing.addMinute(id)
            } else {
                editor.edit(id, "add_minute") { timer, now -> timer.addMinute(now) }
            }
        }
    }

    fun onStop(id: TimerId) = ringing.stop(id)

    fun onEvidence(id: TimerId, enabled: Boolean) = edit(id, "evidence") { timer, _ -> timer.withEvidence(enabled) }

    private fun edit(id: TimerId, command: String, transition: (Timer, TimerAnchor) -> TimerTransition) {
        viewModelScope.launch { editor.edit(id, command, transition = transition) }
    }

    private fun draftLength(): Duration = Duration.ofHours(hours.value.toLong())
        .plusMinutes(minutes.value.toLong())
        .plusSeconds(seconds.value.toLong())

    private data class Draft(val hours: Int, val minutes: Int, val seconds: Int, val label: String, val recordOverride: Boolean?)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MINUTES_PER_HOUR = 60
    }
}
