package dev.upyet.stopwatch.domain

import java.time.Duration

/**
 * The stopwatch, as stored. It counts up and never rings, records or reaches History (AGENTS.md §1, §2 invariant 11).
 *
 * Time is never stored as a running total: [accumulated] is what earlier runs added up to, and [runningSince] is the
 * `SystemClock.elapsedRealtime()` reading of the current run's start, so the shown time is a pure function of the
 * stored anchor and survives process death. Elapsed realtime restarts at boot, which is why [bootCount] is kept
 * with any non-idle stopwatch: a stopwatch from an earlier boot cannot say how long it ran, so it resets
 * ([reconcile]). [laps] holds each lap's total, oldest first; a lap's split is derived from the one before it.
 */
data class Stopwatch(
    val accumulated: Duration = Duration.ZERO,
    val runningSince: Duration? = null,
    val bootCount: Int? = null,
    val laps: List<Duration> = emptyList(),
) {
    val isRunning: Boolean get() = runningSince != null

    /** Idle: never started, or reset. Neither running nor holding time or laps. */
    val isIdle: Boolean get() = !isRunning && accumulated.isZero && laps.isEmpty()

    /** The total shown at elapsed realtime [now], clamped so a clock read before the anchor never goes negative. */
    fun elapsedAt(now: Duration): Duration {
        val run = runningSince?.let { now.minus(it) }?.takeUnless { it.isNegative } ?: Duration.ZERO
        return accumulated.plus(run)
    }

    /** Starts or resumes. A running stopwatch is unchanged, so a double tap cannot move the anchor. */
    fun start(now: Duration, boot: Int?): Stopwatch = if (isRunning) this else copy(runningSince = now, bootCount = boot)

    /** Folds the current run into [accumulated]. A stopwatch that is not running is unchanged. */
    fun pause(now: Duration): Stopwatch = if (isRunning) copy(accumulated = elapsedAt(now), runningSince = null) else this

    /** Records a lap at [now]. Only a running stopwatch takes laps. */
    fun lap(now: Duration): Stopwatch = if (isRunning) copy(laps = laps + elapsedAt(now)) else this

    fun reset(): Stopwatch = IDLE

    /**
     * Resets a stopwatch that belongs to an earlier boot: its boot count differs from [boot], or, when the boot
     * count is unknown on either side, elapsed realtime has gone back past the anchor. That fallback cannot catch
     * a paused stopwatch, which has no anchor; the framework writes the boot count on every boot from API 24, so
     * the unknown case is defensive, and resetting every paused stopwatch on it would wipe it on every launch.
     */
    fun reconcile(now: Duration, boot: Int?): Stopwatch {
        if (isIdle) return this
        val otherBoot = bootCount != null && boot != null && bootCount != boot
        val clockWentBack = runningSince != null && now < runningSince
        return if (otherBoot || clockWentBack) IDLE else this
    }

    /** Laps with their split, oldest first. */
    fun lapRows(): List<LapRow> = laps.mapIndexed { index, total ->
        LapRow(number = index + 1, split = total.minus(laps.getOrNull(index - 1) ?: Duration.ZERO), total = total)
    }

    companion object {
        val IDLE = Stopwatch()
    }
}

data class LapRow(val number: Int, val split: Duration, val total: Duration)
