package dev.upyet.core.directboot

import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerRunState
import java.time.Duration
import java.time.Instant

/** The device-protected copy of this timer: everything ADR 0008 lists, nothing else. */
fun Timer.toMirrored(): MirroredTimer {
    val running = runState as? TimerRunState.Running
    return MirroredTimer(
        timerId = id,
        state = when (runState) {
            TimerRunState.Idle -> MirroredTimerState.IDLE
            is TimerRunState.Running -> MirroredTimerState.RUNNING
            is TimerRunState.Paused -> MirroredTimerState.PAUSED
            TimerRunState.Ringing -> MirroredTimerState.RINGING
            TimerRunState.Missed -> MirroredTimerState.MISSED
        },
        length = length,
        remainingAtAnchor = when (runState) {
            is TimerRunState.Running -> runState.remainingAtAnchor
            is TimerRunState.Paused -> runState.remaining
            TimerRunState.Idle, TimerRunState.Ringing, TimerRunState.Missed -> null
        },
        wallClockAnchor = running?.anchor?.wallClock,
        endsAt = running?.let { it.anchor.wallClock.plus(it.remainingAtAnchor) },
        label = label,
        ringtoneUri = ringtoneUri,
        evidenceEnabled = evidenceEnabled,
    )
}

/**
 * Rebuilds the timer this record describes, for the pre-unlock path and for reconciliation. The mirror has no
 * elapsed anchor, so a Running timer is re-based onto [now]'s elapsed clock at the instant its wall-clock anchor
 * stands for. That keeps the remaining time unclamped, so discovery still sees how overdue it is. [createdAt]
 * is not mirrored; callers pass Room's value, or [Instant.EPOCH] before first unlock.
 *
 * Null when the record is inconsistent (a Running record without its anchor, a Paused one without its
 * remaining), so one bad record never stops the others from ringing.
 */
fun MirroredTimer.toTimer(now: TimerAnchor, createdAt: Instant): Timer? {
    val runState = toRunState(now) ?: return null
    return runCatching {
        Timer.restore(timerId, length, label, ringtoneUri, evidenceEnabled, runState, createdAt)
    }.getOrNull()
}

private fun MirroredTimer.toRunState(now: TimerAnchor): TimerRunState? = when (state) {
    MirroredTimerState.IDLE -> TimerRunState.Idle

    MirroredTimerState.RUNNING -> {
        val remaining = remainingAtAnchor
        val wallClock = wallClockAnchor
        if (remaining == null || wallClock == null || remaining.isNegative) {
            null
        } else {
            val sinceAnchor = Duration.between(wallClock, now.wallClock)
            TimerRunState.Running(remaining, TimerAnchor(now.elapsedRealtime.minus(sinceAnchor), wallClock))
        }
    }

    MirroredTimerState.PAUSED -> remainingAtAnchor?.takeUnless { it.isNegative }?.let(TimerRunState::Paused)

    MirroredTimerState.RINGING -> TimerRunState.Ringing

    MirroredTimerState.MISSED -> TimerRunState.Missed
}
