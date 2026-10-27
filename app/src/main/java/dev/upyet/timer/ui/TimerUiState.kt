package dev.upyet.timer.ui

import androidx.compose.runtime.Immutable
import dev.upyet.timer.domain.TimerId
import java.time.Duration
import java.time.Instant

/**
 * The Timer tab: the wheels and the new timer's label and **Record when it rings** switch on top, every timer
 * below in ADR 0008's bands. [recordNewTimer] is seeded from the global "Record when a timer rings" default
 * until the user flips it (AGENTS.md §2 invariant 12).
 */
@Immutable
data class TimerUiState(
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0,
    val label: String = "",
    val recordNewTimer: Boolean = false,
    val timers: List<TimerRowState> = emptyList(),
) {
    val canStart: Boolean get() = hours > 0 || minutes > 0 || seconds > 0
}

/** The state tag a row shows; the declaration order is the list's band order (ADR 0008). */
enum class TimerTag { RINGING, MISSED, RUNNING, PAUSED, IDLE }

/**
 * One dense row. Which controls it offers is a function of [tag] alone, so the rules live here and not in
 * the composable: a Missed timer has exactly ADR 0008's three exits (+1 min, Reset, delete), an Idle timer
 * never offers +1 min, and a Ringing timer offers only what the ringing screen offers (Stop, +1 min).
 */
@Immutable
data class TimerRowState(
    val id: TimerId,
    val label: String,
    val tag: TimerTag,
    val remaining: Duration,
    val length: Duration,
    val evidenceEnabled: Boolean,
    /** The wall-clock instant a Running timer reaches zero (`Timer.endsAt`); null in every other state, because a paused timer's ring time would drift. */
    val ringsAt: Instant? = null,
) {
    val canStart: Boolean get() = tag == TimerTag.IDLE
    val canPause: Boolean get() = tag == TimerTag.RUNNING
    val canResume: Boolean get() = tag == TimerTag.PAUSED
    val canStop: Boolean get() = tag == TimerTag.RINGING
    val canAddMinute: Boolean get() = tag != TimerTag.IDLE
    val canReset: Boolean get() = tag == TimerTag.RUNNING || tag == TimerTag.PAUSED || tag == TimerTag.MISSED
    val canDelete: Boolean get() = tag != TimerTag.RINGING

    /** The REC chip is shown on every row but Missed, and enabled only in Idle, Running and Paused (ADR 0010). */
    val showsEvidenceChip: Boolean get() = tag != TimerTag.MISSED
    val canToggleEvidence: Boolean get() = tag == TimerTag.IDLE || tag == TimerTag.RUNNING || tag == TimerTag.PAUSED
}
