package dev.upyet.alarm.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.R
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.NextAlarm
import dev.upyet.alarm.domain.NextAlarmInfo
import dev.upyet.alarm.domain.SkipNext
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import dev.upyet.alarm.scheduling.AlarmRescheduler
import dev.upyet.alarm.scheduling.AlarmScheduler
import dev.upyet.alarm.scheduling.SchedulingResult
import dev.upyet.alarm.scheduling.SkipNextOccurrence
import dev.upyet.core.time.TimeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class AlarmListUiState(
    val alarms: List<Alarm> = emptyList(),
    @StringRes val errorRes: Int? = null,
    val showReliabilityAction: Boolean = false,
    val nextAlarm: NextAlarmInfo? = null,
    // Refreshed by the ticker so the next-alarm countdown recomposes even when nothing else changed.
    val now: Instant = Instant.EPOCH,
    // Only skips that have not lapsed: a skip stops showing the moment its occurrence would have rung.
    val activeSkips: Map<AlarmId, LocalDate> = emptyMap(),
    val turnOffPrompt: TurnOffPrompt? = null,
)

/** Asked when a recurring alarm's switch is turned off, because the user may only have meant to skip [skipDate]. */
data class TurnOffPrompt(val alarmId: AlarmId, val time: LocalTime, val skipDate: LocalDate)

private const val TICK_INTERVAL_MS = 30_000L

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val rescheduler: AlarmRescheduler,
    private val scheduler: AlarmScheduler,
    private val timeProvider: TimeProvider,
    private val skipNextOccurrence: SkipNextOccurrence,
) : ViewModel() {
    private val schedulingError = MutableStateFlow<Int?>(null)
    private val failedIds = MutableStateFlow<Set<AlarmId>>(emptySet())

    // Holds only the id: the prompt itself is derived on every tick, so its date never goes stale.
    private val turnOffPromptFor = MutableStateFlow<AlarmId?>(null)

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
        turnOffPromptFor,
    ) { alarms, error, failed, _, promptFor ->
        val now = timeProvider.now()
        val zone = timeProvider.zone()
        val visibleAlarms = alarms.map { if (it.id in failed) it.copy(enabled = false) else it }
        AlarmListUiState(
            alarms = visibleAlarms,
            errorRes = error,
            showReliabilityAction = error != null,
            nextAlarm = NextAlarm.select(visibleAlarms, zone, now),
            now = now,
            activeSkips = visibleAlarms.mapNotNull { alarm -> SkipNext.activeDate(alarm, zone, now)?.let { alarm.id to it } }.toMap(),
            turnOffPrompt = visibleAlarms.firstOrNull { it.id == promptFor }?.let { openPrompt(it, zone, now) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmListUiState())

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch {
        turnOffPromptFor.value = null
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
        } else if (turnOffIsAmbiguous(alarm)) {
            // The switch stays on: nothing changes until the user answers the prompt.
            turnOffPromptFor.value = alarm.id
        } else {
            turnOff(alarm)
        }
    }

    /** The prompt's answer "turn it off": indefinite, so any skip and pending snooze go with it. */
    fun confirmTurnOff() = viewModelScope.launch {
        val id = turnOffPromptFor.value ?: return@launch
        turnOffPromptFor.value = null
        repository.getAlarm(id)?.let { turnOff(it) }
    }

    /** The prompt's answer "only skip the next one". A pending snooze is cancelled too: the user asked for quiet. */
    fun skipInsteadOfTurnOff() = viewModelScope.launch {
        val id = turnOffPromptFor.value ?: return@launch
        turnOffPromptFor.value = null
        val alarm = repository.getAlarm(id) ?: return@launch
        scheduler.cancel(alarm.id, AlarmOccurrenceKind.SNOOZE)
        skipNextOccurrence.skip(alarm)?.let { handleSkipResult(alarm.id, it) }
    }

    fun dismissTurnOffPrompt() {
        turnOffPromptFor.value = null
    }

    /**
     * Keeps an open prompt on screen while its alarm is still on, even if a skip arrives meanwhile (say from
     * the upcoming-alarm notification): hiding it would leave the id set and the dialog free to reappear once
     * that skip lapsed. Either answer stays correct, since skipping an already skipped alarm is a no-op.
     */
    private fun openPrompt(alarm: Alarm, zone: ZoneId, now: Instant): TurnOffPrompt? {
        if (!alarm.enabled) return null
        val date = SkipNext.activeDate(alarm, zone, now) ?: SkipNext.target(alarm, zone, now) ?: return null
        return TurnOffPrompt(alarm.id, alarm.time, date)
    }

    /** Turning [alarm] off is ambiguous only when skipping is a real alternative: recurring, enabled, not already skipping. */
    private fun turnOffIsAmbiguous(alarm: Alarm): Boolean {
        val zone = timeProvider.zone()
        val now = timeProvider.now()
        return SkipNext.activeDate(alarm, zone, now) == null && SkipNext.target(alarm, zone, now) != null
    }

    private suspend fun turnOff(alarm: Alarm) {
        scheduler.cancel(alarm.id, AlarmOccurrenceKind.MAIN)
        scheduler.cancel(alarm.id, AlarmOccurrenceKind.SNOOZE)
        failedIds.value -= alarm.id
        // Turning off is indefinite, so a skip must not survive to resurface when the alarm is turned back on.
        if (alarm.skipNextOn != null) repository.setSkipNextOn(alarm.id, null)
        repository.setEnabled(alarm.id, false)
        rescheduler.refreshUpcoming()
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
        rescheduler.refreshUpcoming()
    }

    fun toggleSkipNext(alarm: Alarm) = viewModelScope.launch {
        if (!SkipNext.canSkip(alarm)) return@launch
        val isSkipped = SkipNext.activeDate(alarm, timeProvider.zone(), timeProvider.now()) != null
        val result = if (isSkipped) skipNextOccurrence.unskip(alarm) else skipNextOccurrence.skip(alarm)
        result?.let { handleSkipResult(alarm.id, it) }
    }

    private suspend fun handleSkipResult(id: AlarmId, result: SchedulingResult) {
        when (result) {
            SchedulingResult.Scheduled -> {
                failedIds.value -= id
                schedulingError.value = null
            }

            SchedulingResult.ExactAlarmsUnavailable -> schedulingFailed(id, R.string.alarm_scheduling_exact_unavailable)

            is SchedulingResult.Failed -> schedulingFailed(id, R.string.alarm_scheduling_failed)
        }
    }
}
