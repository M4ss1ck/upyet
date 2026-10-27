package dev.upyet.timer.ringing

import dagger.Lazy
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.core.directboot.UserUnlockState
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.time.TimeProvider
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.evidence.domain.TimerAttribution
import dev.upyet.timer.playback.RingingTimer
import dev.upyet.timer.playback.TimerRingingRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Where the showing's occurrence is, as the evidence request needs it. */
sealed interface ShowingOccurrence {
    /** No recording-on timer has rung in this showing; nothing is recorded. */
    data object None : ShowingOccurrence

    /**
     * The row is being inserted. Recording waits, so its first segment attaches to the row rather than being written
     * with no occurrence and orphaned.
     */
    data object Creating : ShowingOccurrence

    /** Before first unlock, or the insert failed: the showing records without an occurrence (AGENTS.md §11). */
    data object Unavailable : ShowingOccurrence

    data class Open(val id: OccurrenceId) : ShowingOccurrence
}

/**
 * Owns the one timer occurrence of a showing of the timer ringing screen (AGENTS.md §2 invariants 11 and 13, ADR 0010).
 * The occurrence spans the showing, not the recording: it opens with the first recording-on ring, takes timers that
 * join while the showing is up, and completes only when the showing ends, so a recording-on timer leaving while others
 * still ring keeps it open. Room is behind [Lazy] and is never built before first unlock (AGENTS.md §11).
 *
 * Process-scoped, because the showing outlives the screen: swiping [TimerRingingActivity] away from recents clears its
 * ViewModel while the timers still ring, and the relaunched screen must join the occurrence the first one opened. A
 * showing is identified by [TimerRingingRegistry.showing], so a showing that ended while no screen was collecting is
 * interrupted here rather than left open.
 */
@Singleton
class TimerShowingOccurrence @Inject constructor(
    private val repository: Lazy<OccurrenceRepository>,
    private val userUnlockState: UserUnlockState,
    private val timeProvider: TimeProvider,
    private val registry: TimerRingingRegistry,
) {
    private val mutableState = MutableStateFlow<ShowingOccurrence>(ShowingOccurrence.None)
    val state: StateFlow<ShowingOccurrence> = mutableState.asStateFlow()

    /** The recording-on timers the open occurrence already lists, first seen first. Empty whenever nothing is open. */
    private var attributed: List<TimerAttribution> = emptyList()

    /** The [TimerRingingRegistry.showing] number the current [mutableState] was made in. */
    private var openedIn = 0L

    /**
     * Several ViewModels can overlap while one is cleared and the next starts, so every step holds [lock]: the state
     * and the attribution are only ever read and written by one emission at a time.
     */
    private val lock = Mutex()

    /**
     * Called with every registry emission from one collector. [ringing] is the whole showing, not a change to it, which
     * is why an empty list is the only signal that the showing ended, unless the showing number moved on without one.
     */
    suspend fun onRinging(ringing: List<RingingTimer>) {
        lock.withLock { advance(ringing) }
    }

    private suspend fun advance(ringing: List<RingingTimer>) {
        val recordingOn = ringing.filter { it.evidenceEnabled }
        endPreviousShowingIfChanged()
        when (val current = mutableState.value) {
            ShowingOccurrence.None -> if (recordingOn.isNotEmpty()) open(recordingOn)

            // Creating only lives inside advance, which holds [lock], so no emission sees it in flight.
            ShowingOccurrence.Creating -> Unit

            ShowingOccurrence.Unavailable -> if (ringing.isEmpty()) mutableState.value = ShowingOccurrence.None

            is ShowingOccurrence.Open -> if (ringing.isEmpty()) close(current.id) else join(current.id, recordingOn)
        }
    }

    /**
     * The showing number moved since this occurrence was opened, so the old showing ended without an empty list
     * reaching this class. Nobody ended it from the screen, so it is interrupted with no user-ended time.
     */
    private suspend fun endPreviousShowingIfChanged() {
        if (registry.showing.value == openedIn) return
        when (val current = mutableState.value) {
            ShowingOccurrence.None, ShowingOccurrence.Creating -> Unit
            is ShowingOccurrence.Open -> interrupt(current.id)
            ShowingOccurrence.Unavailable -> mutableState.value = ShowingOccurrence.None
        }
    }

    private suspend fun open(recordingOn: List<RingingTimer>) {
        openedIn = registry.showing.value
        if (!userUnlockState.isUserUnlocked()) {
            mutableState.value = ShowingOccurrence.Unavailable
            return
        }
        val timers = recordingOn.map { it.toAttribution() }
        val triggeredAt = recordingOn.minOf { it.ringingSince }
        mutableState.value = ShowingOccurrence.Creating
        // open is reached only from None, so this process holds no open occurrence and every timer ring still RINGING
        // belongs to a showing whose process died. A failed sweep must not stop this showing from recording.
        attempt("sweep") { repository.get().interruptOrphanedTimerOccurrences() }
        val id = attempt("create") { repository.get().createTimerOccurrence(timers, triggeredAt, triggeredAt) }
        if (id == null) {
            mutableState.value = ShowingOccurrence.Unavailable
            return
        }
        attributed = timers
        mutableState.value = ShowingOccurrence.Open(id)
    }

    /**
     * The showing ended. The state moves to None even when the completion write fails: a showing that has ended must
     * never keep an occurrence open for the next ring to attach to.
     */
    private suspend fun close(id: OccurrenceId) {
        attempt("complete") { repository.get().completeOccurrence(id, timeProvider.now(), OccurrenceOutcome.STOPPED) }
        attributed = emptyList()
        mutableState.value = ShowingOccurrence.None
    }

    /**
     * A showing that ended unseen. Its user-ended time is null because the user never ended it from the screen. Like
     * [close], the state moves to None even when the write fails.
     */
    private suspend fun interrupt(id: OccurrenceId) {
        attempt("interrupt") { repository.get().completeOccurrence(id, null, OccurrenceOutcome.INTERRUPTED) }
        attributed = emptyList()
        mutableState.value = ShowingOccurrence.None
    }

    /** Only a timer not yet listed is added, so a timer ringing again keeps its place. A failed write is retried next time. */
    private suspend fun join(id: OccurrenceId, recordingOn: List<RingingTimer>) {
        val joined = recordingOn
            .map { it.toAttribution() }
            .filter { timer -> attributed.none { it.timerId == timer.timerId } }
        if (joined.isEmpty()) return
        val full = attributed + joined
        if (attempt("update") { repository.get().updateTimerAttribution(id, full) } != null) attributed = full
    }

    /** A failed write is logged, never thrown: the ring must go on (AGENTS.md §2 invariant 3). Cancellation is not a failure. */
    private suspend fun <T> attempt(step: String, block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        AlarmLog.event("timer_occurrence_error", "step" to step, "error" to failure::class.simpleName)
        null
    }

    private fun RingingTimer.toAttribution() = TimerAttribution(id, label)
}
