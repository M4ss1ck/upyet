package dev.upyet.timer.ringing

import dev.upyet.evidence.camera.EvidenceRequest
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.playback.RingingTimer
import java.time.Duration
import java.time.Instant

/** Where the showing is. The Activity may only finish itself on [ENDED], never on the initial [LOADING]. */
enum class TimerRingingPhase { LOADING, RINGING, ENDED }

/** One ringing timer as the screen shows it: [overtime] is how long it has been ringing, never negative. */
data class RingingTimerItem(val id: TimerId, val label: String, val length: Duration?, val overtime: Duration)

data class TimerRingingUiState(
    val phase: TimerRingingPhase = TimerRingingPhase.LOADING,
    val focused: RingingTimerItem? = null,
    /** Every other ringing timer, in ringing order, as the chips under the controls. */
    val queue: List<RingingTimerItem> = emptyList(),
    /** At least one ringing timer has Timer evidence on (ADR 0010): the badge shows and the camera records. */
    val recordingOn: Boolean = false,
) {
    /** Stop all only when two or more ring (ADR 0008): the focused timer plus at least one chip. */
    val showStopAll: Boolean get() = focused != null && queue.isNotEmpty()

    /**
     * What the evidence component records for this showing; null while nothing rings and while every ringing timer has
     * Timer evidence off, so such a showing never touches the recorder the alarm path shares. The occurrence id stays
     * null until slice #40 writes the timer occurrence, so no segment row is written and the coordinator keeps no file.
     */
    val evidenceRequest: EvidenceRequest?
        get() = EvidenceRequest(occurrenceId = null, evidenceEnabled = true).takeIf { phase == TimerRingingPhase.RINGING && recordingOn }
}

/**
 * The screen state for one showing (issue #14, variant C). Focus stays on the timer the user chose while it rings;
 * otherwise it is on the timer that started ringing most recently, which is the one the user just heard.
 */
fun timerRingingState(ringing: List<RingingTimer>, now: Instant, selected: TimerId?, sessionSeen: Boolean): TimerRingingUiState {
    if (ringing.isEmpty()) {
        return TimerRingingUiState(phase = if (sessionSeen) TimerRingingPhase.ENDED else TimerRingingPhase.LOADING)
    }
    val focused = ringing.firstOrNull { it.id == selected } ?: ringing.maxBy { it.ringingSince }
    return TimerRingingUiState(
        phase = TimerRingingPhase.RINGING,
        focused = focused.item(now),
        queue = ringing.filter { it.id != focused.id }.map { it.item(now) },
        recordingOn = ringing.any { it.evidenceEnabled },
    )
}

private fun RingingTimer.item(now: Instant) =
    RingingTimerItem(id, label, length, Duration.between(ringingSince, now).coerceAtLeast(Duration.ZERO))

/** A duration as the clock digits the screen formats through string resources; whole seconds, never negative. */
data class ClockParts(val hours: Long, val minutes: Int, val seconds: Int) {
    companion object {
        fun of(duration: Duration): ClockParts {
            val total = duration.seconds.coerceAtLeast(0L)
            val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
            val seconds = total % SECONDS_PER_MINUTE
            return ClockParts(total / SECONDS_PER_HOUR, minutes.toInt(), seconds.toInt())
        }

        private const val SECONDS_PER_HOUR = 3_600L
        private const val SECONDS_PER_MINUTE = 60L
    }
}
