package dev.upyet.timer.playback

import dev.upyet.timer.domain.TimerId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One timer taking part in the current ringing. What the ringing screen (slice #38) and the notification
 * need, copied out of the store when the ring starts, so neither of them reads Room or the mirror.
 */
data class RingingTimer(
    val id: TimerId,
    val label: String,
    /** The chosen length; null only when the store could not be read and the timer rang anyway. */
    val length: Duration?,
    val ringtoneUri: String?,
    /** GLOSSARY.md: Timer evidence. False when [detailsUnreadable]: nothing records for a toggle nobody could read. */
    val evidenceEnabled: Boolean,
    val ringingSince: Instant,
    /**
     * The store could not be read in time, so label, length, ringtone and evidence are defaults. The heads-up then
     * offers no Stop either, because the ring might be a recording one ([TimerHeadsUp]).
     */
    val detailsUnreadable: Boolean = false,
)

/**
 * Every timer ringing right now, oldest first (ADR 0008). The alarm path's single-slot
 * `RingingSessionRegistry` cannot hold several rings, so timers get their own. Only [TimerRinging] writes it.
 */
@Singleton
class TimerRingingRegistry @Inject constructor() {
    private val _ringing = MutableStateFlow<List<RingingTimer>>(emptyList())
    val ringing: StateFlow<List<RingingTimer>> = _ringing.asStateFlow()

    internal fun add(timer: RingingTimer) {
        _ringing.update { current -> if (current.any { it.id == timer.id }) current else current + timer }
    }

    internal fun remove(id: TimerId) {
        _ringing.update { current -> current.filterNot { it.id == id } }
    }

    internal fun clear() {
        _ringing.value = emptyList()
    }
}
