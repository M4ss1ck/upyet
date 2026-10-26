package dev.upyet.timer.domain

import java.time.Duration
import java.time.Instant

/**
 * A countdown and its place in ADR 0008's transition table. Every member function is one row of that table
 * and returns a [TimerTransition]; none of them reads a clock, so callers pass `now`.
 *
 * The constructor and `copy` are private so the table cannot be bypassed: a timer comes from [create], from
 * [restore] (persistence only), or from a transition. [withEvidence] is how the ADR 0010 rule "editable until
 * it rings" stays a domain rule rather than a UI convention.
 */
@ConsistentCopyVisibility
data class Timer private constructor(
    val id: TimerId,
    /** The chosen length; Reset restores it and +1 min never changes it. */
    val length: Duration,
    val label: String,
    val ringtoneUri: String?,
    /** GLOSSARY.md: Timer evidence. Takes the global switch at creation; see [withEvidence]. */
    val evidenceEnabled: Boolean,
    val runState: TimerRunState,
    val createdAt: Instant,
) {
    init {
        require(length > Duration.ZERO) { "length must be positive" }
    }

    /** Time left at [now]: the full length when Idle, zero when Ringing or Missed. */
    fun remaining(now: TimerAnchor): Duration = when (runState) {
        TimerRunState.Idle -> length
        is TimerRunState.Running -> RemainingTime.of(runState.remainingAtAnchor, runState.anchor, now)
        is TimerRunState.Paused -> runState.remaining
        TimerRunState.Ringing, TimerRunState.Missed -> Duration.ZERO
    }

    /** Start from Idle at the full length, or resume from Paused with what was left. */
    fun start(now: TimerAnchor): TimerTransition = when (runState) {
        TimerRunState.Idle -> applied(TimerRunState.Running(length, now))
        is TimerRunState.Paused -> applied(TimerRunState.Running(runState.remaining, now))
        is TimerRunState.Running, TimerRunState.Ringing, TimerRunState.Missed -> TimerTransition.NoOp
    }

    /** Freeze the remaining time. Not offered while Ringing or Missed: there is nothing left to pause. */
    fun pause(now: TimerAnchor): TimerTransition = when (runState) {
        is TimerRunState.Running -> applied(TimerRunState.Paused(remaining(now)))
        TimerRunState.Idle, is TimerRunState.Paused -> TimerTransition.NoOp
        TimerRunState.Ringing, TimerRunState.Missed -> TimerTransition.NotOffered
    }

    /** Back to Idle at the full length, from anywhere but Idle. */
    fun reset(): TimerTransition = when (runState) {
        TimerRunState.Idle -> TimerTransition.NoOp
        is TimerRunState.Running, is TimerRunState.Paused, TimerRunState.Ringing, TimerRunState.Missed -> applied(TimerRunState.Idle)
    }

    /**
     * +1 min. A running or paused timer gains a minute; a ringing or missed one restarts at one minute from
     * [now]. Not offered on Idle, where starting from 60 s would be a different action wearing the same name.
     */
    fun addMinute(now: TimerAnchor): TimerTransition = when (runState) {
        TimerRunState.Idle -> TimerTransition.NotOffered
        is TimerRunState.Running -> applied(TimerRunState.Running(remaining(now).plus(ONE_MINUTE), now))
        is TimerRunState.Paused -> applied(TimerRunState.Paused(runState.remaining.plus(ONE_MINUTE)))
        TimerRunState.Ringing, TimerRunState.Missed -> applied(TimerRunState.Running(ONE_MINUTE, now))
    }

    /**
     * The wall-clock instant a Running timer reaches zero: its anchor plus what was left there. It is a pure
     * function of the stored anchors, so the alarm that delivery carries can be compared with it exactly; it
     * changes with every pause, resume, +1 min, reset and re-anchoring. Null in every other state.
     */
    val endsAt: Instant?
        get() = (runState as? TimerRunState.Running)?.let { it.anchor.wallClock.plus(it.remainingAtAnchor) }

    /**
     * The exact alarm fired, carrying the [endsAt] it was armed for. Only a Running timer whose end still
     * matches, to the millisecond the alarm carries, starts ringing. Anything else is stale: the timer was
     * cancelled, paused, reset, extended or re-anchored after the alarm was armed (AGENTS.md §6a).
     */
    fun onExpiry(deliveredEndsAt: Instant): TimerTransition = when (runState) {
        is TimerRunState.Running ->
            if (endsAt?.toEpochMilli() == deliveredEndsAt.toEpochMilli()) applied(TimerRunState.Ringing) else TimerTransition.NoOp

        TimerRunState.Idle, is TimerRunState.Paused, TimerRunState.Ringing, TimerRunState.Missed -> TimerTransition.NoOp
    }

    /** Stop a ringing timer: it returns to Idle at its full length and stays in the list. */
    fun stop(): TimerTransition = when (runState) {
        TimerRunState.Ringing -> applied(TimerRunState.Idle)
        TimerRunState.Idle, is TimerRunState.Running, is TimerRunState.Paused, TimerRunState.Missed -> TimerTransition.NoOp
    }

    fun delete(): TimerTransition = TimerTransition.Removed

    /**
     * Boot or discovery (`LOCKED_BOOT_COMPLETED`, `USER_UNLOCKED`, app launch; AGENTS.md §6a). A Running timer
     * more than [MISSED_AFTER] overdue becomes Missed. Otherwise it is re-anchored at [now], remaining clamped
     * at zero: a timer 0 to 60 s overdue rings through the immediate exact alarm that re-arming programs, so
     * the platform still authorises the foreground service, and its expiry is what moves it to Ringing.
     * A Ringing timer is returned unchanged as [TimerTransition.Applied] because it must ring again.
     *
     * [afterBoot] measures with the wall clock only: elapsed realtime restarted, so even a positive elapsed
     * delta compares two different boots.
     */
    fun onDiscovery(now: TimerAnchor, afterBoot: Boolean): TimerTransition = when (runState) {
        is TimerRunState.Running -> {
            val raw = RemainingTime.raw(runState.remainingAtAnchor, runState.anchor, now, afterBoot)
            if (raw.negated() > MISSED_AFTER) {
                applied(TimerRunState.Missed)
            } else {
                applied(TimerRunState.Running(if (raw.isNegative) Duration.ZERO else raw, now))
            }
        }

        TimerRunState.Ringing -> TimerTransition.Applied(this)

        TimerRunState.Idle, is TimerRunState.Paused, TimerRunState.Missed -> TimerTransition.NoOp
    }

    /**
     * `TIME_SET`: re-anchor a Running timer from the elapsed delta, so its end on the elapsed clock (and the
     * exact alarm armed for it) does not move. A negative elapsed delta is ignored (AGENTS.md §6a).
     */
    fun onWallClockChanged(now: TimerAnchor): TimerTransition {
        if (runState !is TimerRunState.Running) return TimerTransition.NoOp
        if (RemainingTime.elapsedDelta(runState.anchor, now).isNegative) return TimerTransition.NoOp
        return applied(TimerRunState.Running(RemainingTime.fromElapsed(runState.remainingAtAnchor, runState.anchor, now), now))
    }

    /** `TIMEZONE_CHANGED`: both anchors are zone-free, so no timer changes. */
    fun onTimeZoneChanged(): TimerTransition = TimerTransition.NoOp

    /**
     * Timer evidence can change while Idle, Running or Paused (ADR 0010, AGENTS.md §12): not while Ringing,
     * where the screen offers Stop, Stop all and +1 min and nothing else, nor while Missed, which the ADR does
     * not admit; +1 min or Reset brings a missed timer back to a state where it can change.
     */
    fun withEvidence(enabled: Boolean): TimerTransition = when (runState) {
        TimerRunState.Ringing, TimerRunState.Missed -> TimerTransition.NotOffered

        TimerRunState.Idle, is TimerRunState.Running, is TimerRunState.Paused ->
            if (enabled == evidenceEnabled) TimerTransition.NoOp else TimerTransition.Applied(copy(evidenceEnabled = enabled))
    }

    private fun applied(state: TimerRunState) = TimerTransition.Applied(copy(runState = state))

    companion object {
        val ONE_MINUTE: Duration = Duration.ofMinutes(1)

        /** Discovered later than this past its end, a timer is Missed instead of ringing late (ADR 0008). */
        val MISSED_AFTER: Duration = Duration.ofSeconds(60)

        /** A new timer is Idle; [evidenceEnabled] is the global "Record when a timer rings" value (ADR 0010). */
        fun create(
            id: TimerId,
            length: Duration,
            label: String,
            ringtoneUri: String?,
            evidenceEnabled: Boolean,
            createdAt: Instant,
        ): Timer = Timer(id, length, label, ringtoneUri, evidenceEnabled, TimerRunState.Idle, createdAt)

        /** Rebuilds a stored timer exactly as it was saved. For persistence only, never for changing state. */
        fun restore(
            id: TimerId,
            length: Duration,
            label: String,
            ringtoneUri: String?,
            evidenceEnabled: Boolean,
            runState: TimerRunState,
            createdAt: Instant,
        ): Timer = Timer(id, length, label, ringtoneUri, evidenceEnabled, runState, createdAt)
    }
}
