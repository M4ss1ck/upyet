package dev.upyet.alarm.ringing

import android.content.Context
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.alarm.playback.AlarmPlaybackService
import dev.upyet.alarm.playback.RingingSessionRegistry
import dev.upyet.core.time.TimeProvider
import dev.upyet.evidence.camera.EvidenceCoordinator
import dev.upyet.evidence.camera.EvidenceRecordingState
import dev.upyet.evidence.domain.OccurrenceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Where the ringing interaction currently is. The Activity may only finish itself on [ENDED]. */
enum class RingingPhase { LOADING, RINGING, ENDED }

data class RingingUiState(
    val phase: RingingPhase = RingingPhase.LOADING,
    val currentTime: LocalTime = LocalTime.MIDNIGHT,
    val currentDate: LocalDate = LocalDate.of(1970, 1, 1),
    val scheduledTime: LocalTime = LocalTime.MIDNIGHT,
    val label: String? = null,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val evidenceEnabled: Boolean = false,
    val occurrenceId: OccurrenceId? = null,
    val isSilentAlarmStream: Boolean = false,
    val snoozeAllowed: Boolean = true,
    val isLastSnooze: Boolean = false,
) {
    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 9
    }
}

@HiltViewModel
class RingingViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val registry: RingingSessionRegistry,
    private val timeProvider: TimeProvider,
    private val evidenceCoordinator: EvidenceCoordinator,
    private val occurrenceRepository: OccurrenceRepository,
) : ViewModel() {
    // The session starts as null while the service is still publishing it, so the phase stays LOADING
    // until a session has actually been seen; only afterwards does a null session mean "ringing ended".
    private var sessionSeen = false

    private val ticker: Flow<Instant> =
        flow {
            while (true) {
                emit(timeProvider.now())
                delay(TICK_MILLIS)
            }
        }

    val state: StateFlow<RingingUiState> =
        combine(registry.session, ticker) { session, now ->
            if (session != null) sessionSeen = true
            val zone = timeProvider.zone()
            RingingUiState(
                phase =
                when {
                    session != null -> RingingPhase.RINGING
                    sessionSeen -> RingingPhase.ENDED
                    else -> RingingPhase.LOADING
                },
                currentTime = now.atZone(zone).toLocalTime(),
                currentDate = now.atZone(zone).toLocalDate(),
                scheduledTime =
                session?.scheduledFor?.atZone(zone)?.toLocalTime()
                    ?: LocalTime.MIDNIGHT,
                label = session?.label,
                snoozeMinutes = session?.snoozeMinutes ?: RingingUiState.DEFAULT_SNOOZE_MINUTES,
                evidenceEnabled = session?.evidenceEnabled ?: false,
                occurrenceId = session?.occurrenceId,
                isSilentAlarmStream = session?.isSilentAlarmStream ?: false,
                snoozeAllowed = session?.budget?.isExhausted?.not() ?: true,
                isLastSnooze = session?.budget?.isLastSnooze ?: false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RingingUiState())

    val evidenceState: StateFlow<EvidenceRecordingState> = evidenceCoordinator.state
    val surfaceRequest: StateFlow<SurfaceRequest?> = evidenceCoordinator.surfaceRequest
    private val markedVisibleOccurrences = mutableSetOf<OccurrenceId>()

    fun onRingingVisible(lifecycleOwner: LifecycleOwner) {
        val current = state.value
        if (current.phase != RingingPhase.RINGING) return
        val occurrenceId = current.occurrenceId
        if (occurrenceId != null && markedVisibleOccurrences.add(occurrenceId)) {
            viewModelScope.launch {
                occurrenceRepository.markActivityVisible(occurrenceId, timeProvider.now())
            }
        }
        viewModelScope.launch {
            evidenceCoordinator.start(lifecycleOwner, occurrenceId, current.evidenceEnabled)
        }
    }

    fun onRingingHidden() {
        evidenceCoordinator.stop()
        evidenceCoordinator.release()
    }

    fun dismiss() {
        // Evidence is finalized asynchronously; the alarm command is never delayed by the camera.
        evidenceCoordinator.stop()
        sendCommand(AlarmPlaybackService.dismissIntent(context))
    }

    fun snooze() {
        // Evidence is finalized asynchronously; the alarm command is never delayed by the camera.
        evidenceCoordinator.stop()
        sendCommand(AlarmPlaybackService.snoozeIntent(context))
    }

    private fun sendCommand(intent: android.content.Intent) {
        // startForegroundService keeps working even if the service was recreated between ringing and tap.
        ContextCompat.startForegroundService(context, intent)
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
