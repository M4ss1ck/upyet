package dev.myalarm.alarm.ringing

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.myalarm.alarm.playback.AlarmPlaybackService
import dev.myalarm.alarm.playback.RingingSessionRegistry
import dev.myalarm.core.time.TimeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

/** Where the ringing interaction currently is. The Activity may only finish itself on [ENDED]. */
enum class RingingPhase { LOADING, RINGING, ENDED }

data class RingingUiState(
    val phase: RingingPhase = RingingPhase.LOADING,
    val currentTime: LocalTime = LocalTime.MIDNIGHT,
    val scheduledTime: LocalTime = LocalTime.MIDNIGHT,
    val label: String? = null,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val evidenceEnabled: Boolean = false,
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
                scheduledTime =
                session?.scheduledFor?.atZone(zone)?.toLocalTime()
                    ?: LocalTime.MIDNIGHT,
                label = session?.label,
                snoozeMinutes = session?.snoozeMinutes ?: RingingUiState.DEFAULT_SNOOZE_MINUTES,
                evidenceEnabled = session?.evidenceEnabled ?: false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RingingUiState())

    fun dismiss() = sendCommand(AlarmPlaybackService.dismissIntent(context))

    fun snooze() = sendCommand(AlarmPlaybackService.snoozeIntent(context))

    private fun sendCommand(intent: android.content.Intent) {
        // startForegroundService keeps working even if the service was recreated between ringing and tap.
        ContextCompat.startForegroundService(context, intent)
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
