package dev.upyet.core.time

import android.os.SystemClock
import dev.upyet.timer.domain.TimerAnchor
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Reads both timer clocks at one moment (ADR 0008), so a test can substitute a fixed [TimerAnchor]. */
fun interface TimerClock {
    fun now(): TimerAnchor
}

class SystemTimerClock @Inject constructor() : TimerClock {
    override fun now(): TimerAnchor = TimerAnchor(Duration.ofMillis(SystemClock.elapsedRealtime()), Instant.now())
}
