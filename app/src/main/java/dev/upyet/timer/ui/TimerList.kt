package dev.upyet.timer.ui

import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerRunState
import java.time.Duration

/**
 * The list the Timer tab shows, in ADR 0008's bands: ringing first so it cannot be missed, then missed, running,
 * paused and idle. Inside a band the order answers what the user looks for: the timer that ends next, or the
 * newest idle one. Pure, so the ordering is pinned by a JVM test and not by a screen.
 */
fun timerRows(timers: List<Timer>, now: TimerAnchor): List<TimerRowState> {
    val byTag = timers.groupBy { tagOf(it.runState) }
    return TimerTag.entries.flatMap { tag ->
        byTag[tag].orEmpty().sortedWith(comparatorFor(tag, now)).map { it.toRow(tag, now) }
    }
}

private fun tagOf(state: TimerRunState): TimerTag = when (state) {
    TimerRunState.Idle -> TimerTag.IDLE
    is TimerRunState.Running -> TimerTag.RUNNING
    is TimerRunState.Paused -> TimerTag.PAUSED
    TimerRunState.Ringing -> TimerTag.RINGING
    TimerRunState.Missed -> TimerTag.MISSED
}

private fun comparatorFor(tag: TimerTag, now: TimerAnchor): Comparator<Timer> = when (tag) {
    TimerTag.RUNNING, TimerTag.PAUSED -> compareBy<Timer> { it.remaining(now) }.thenBy { it.id.value }
    TimerTag.IDLE -> compareByDescending<Timer> { it.createdAt }.thenByDescending { it.id.value }
    TimerTag.RINGING, TimerTag.MISSED -> compareBy { it.id.value }
}

/**
 * The display remaining time is rounded UP to whole seconds: a running timer at 4.2 s shows 5 s, and only a timer
 * that has really reached zero shows 0. Sorting uses the exact remaining time, not this rounded value.
 */
private fun Timer.toRow(tag: TimerTag, now: TimerAnchor): TimerRowState {
    val exact = remaining(now)
    val wholeSeconds = exact.seconds + if (exact.nano > 0) 1 else 0
    return TimerRowState(
        id = id,
        label = label,
        tag = tag,
        remaining = Duration.ofSeconds(wholeSeconds),
        length = length,
        evidenceEnabled = evidenceEnabled,
    )
}
