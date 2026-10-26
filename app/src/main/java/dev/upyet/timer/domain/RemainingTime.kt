package dev.upyet.timer.domain

import java.time.Duration

/**
 * Time left on a running timer, as a pure function of `remainingAtAnchor` and one clock pair (anchor, now),
 * clamped at zero. A negative delta on either clock means that clock cannot be trusted across the gap, so it
 * counts as no time passed rather than as time added back.
 */
object RemainingTime {
    fun fromElapsed(remainingAtAnchor: Duration, anchor: TimerAnchor, now: TimerAnchor): Duration =
        rawFromElapsed(remainingAtAnchor, anchor, now).clampAtZero()

    fun fromWallClock(remainingAtAnchor: Duration, anchor: TimerAnchor, now: TimerAnchor): Duration =
        rawFromWallClock(remainingAtAnchor, anchor, now).clampAtZero()

    /**
     * The elapsed pair while its delta is not negative, the wall-clock pair otherwise: a negative elapsed delta
     * means the device rebooted before the anchor was rewritten. After a reboot the elapsed delta can also be
     * positive and still wrong, so a boot path asks [fromWallClock] directly instead.
     */
    fun of(remainingAtAnchor: Duration, anchor: TimerAnchor, now: TimerAnchor): Duration =
        raw(remainingAtAnchor, anchor, now, afterBoot = false).clampAtZero()

    /** Unclamped: negative means overdue by that much, which discovery needs to tell a late ring from a missed one. */
    internal fun raw(remainingAtAnchor: Duration, anchor: TimerAnchor, now: TimerAnchor, afterBoot: Boolean): Duration =
        if (afterBoot || elapsedDelta(anchor, now).isNegative) {
            rawFromWallClock(remainingAtAnchor, anchor, now)
        } else {
            rawFromElapsed(remainingAtAnchor, anchor, now)
        }

    internal fun elapsedDelta(anchor: TimerAnchor, now: TimerAnchor): Duration = now.elapsedRealtime.minus(anchor.elapsedRealtime)

    private fun rawFromElapsed(remainingAtAnchor: Duration, anchor: TimerAnchor, now: TimerAnchor): Duration =
        remainingAtAnchor.minus(elapsedDelta(anchor, now).clampAtZero())

    private fun rawFromWallClock(remainingAtAnchor: Duration, anchor: TimerAnchor, now: TimerAnchor): Duration =
        remainingAtAnchor.minus(Duration.between(anchor.wallClock, now.wallClock).clampAtZero())

    private fun Duration.clampAtZero(): Duration = if (isNegative) Duration.ZERO else this
}
