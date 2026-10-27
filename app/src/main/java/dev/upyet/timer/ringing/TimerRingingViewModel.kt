package dev.upyet.timer.ringing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.core.time.TimeProvider
import dev.upyet.evidence.camera.EvidenceRecorder
import dev.upyet.evidence.camera.EvidenceRequest
import dev.upyet.evidence.camera.EvidenceSession
import dev.upyet.evidence.data.DeferredEvidenceSink
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.playback.TimerRingingRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Interaction for one showing of the timer ringing screen (AGENTS.md §9a). It reads who rings from
 * [TimerRingingRegistry], which the service alone writes, and sends Stop, +1 min and Stop all to the service; it
 * never plays, vibrates or persists a run state, so every button works whatever the camera is doing.
 */
@HiltViewModel
class TimerRingingViewModel
@Inject
constructor(
    private val registry: TimerRingingRegistry,
    private val commands: TimerRingingCommands,
    private val timeProvider: TimeProvider,
    private val savedStateHandle: SavedStateHandle,
    evidenceRecorder: EvidenceRecorder,
    // Lazy: this screen is Direct Boot aware, so it must not build credential-protected Room before unlock.
    occurrenceRepository: Lazy<OccurrenceRepository>,
    // Process-scoped: the showing outlives this screen, so a relaunch joins the occurrence the first screen opened.
    private val showing: TimerShowingOccurrence,
) : ViewModel() {
    // The registry starts empty while the service is still reading the timer, so only an empty showing that
    // followed a non-empty one has ended.
    private var sessionSeen = false

    private val ticker: Flow<Instant> =
        flow {
            while (true) {
                emit(timeProvider.now())
                delay(TICK_MILLIS)
            }
        }

    private val selected: Flow<TimerId?> = savedStateHandle.getStateFlow<Long?>(KEY_FOCUSED, null).map { it?.let(::TimerId) }

    val state: StateFlow<TimerRingingUiState> =
        combine(registry.ringing, ticker, selected, showing.state) { ringing, now, focused, occurrence ->
            if (ringing.isNotEmpty()) sessionSeen = true
            timerRingingState(ringing, now, focused, sessionSeen, occurrence)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimerRingingUiState())

    // Lives as long as this ViewModel, like the alarm screen's, so one showing keeps one evidence session.
    val evidence = EvidenceSession(evidenceRecorder, DeferredEvidenceSink(occurrenceRepository), viewModelScope, timeProvider)

    init {
        viewModelScope.launch { registry.ringing.collect { showing.onRinging(it) } }

        // Invariant 13: recording stops when the last recording-on timer leaves the showing, however it left (Stop,
        // +1 min, Stop all, or a delete elsewhere), even while recording-off timers still ring. Only that transition
        // stops the recorder: the coordinator is shared with the alarm path, and a showing that never recorded must
        // not cut a ringing alarm's evidence short.
        viewModelScope.launch {
            var wasRecording = false
            registry.ringing.map { ringing -> ringing.any { it.evidenceEnabled } }.distinctUntilChanged().collect { recording ->
                if (wasRecording && !recording) evidence.stopRecording()
                wasRecording = recording
            }
        }
    }

    /** Read live, never from a composed snapshot, so a resume after the showing ended records nothing. */
    fun currentEvidenceRequest(): EvidenceRequest? =
        // The request is null whenever nothing rings, whatever the phase, so this reads no state the flow writes.
        timerRingingState(registry.ringing.value, timeProvider.now(), selected = null, sessionSeen = true, occurrence = showing.state.value)
            .evidenceRequest

    fun focus(id: TimerId) {
        savedStateHandle[KEY_FOCUSED] = id.value
    }

    fun stop(id: TimerId) = commands.stop(id)

    fun addMinute(id: TimerId) = commands.addMinute(id)

    fun stopAll() = commands.stopAll()

    private companion object {
        const val KEY_FOCUSED = "focused_timer_id"
        const val TICK_MILLIS = 1_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
