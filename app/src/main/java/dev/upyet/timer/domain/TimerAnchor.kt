package dev.upyet.timer.domain

import java.time.Duration
import java.time.Instant

/**
 * Both clocks read at one moment: `SystemClock.elapsedRealtime()` and the wall clock. A running timer keeps
 * one as its anchor, and every transition takes one as `now`. Two clocks because neither survives alone:
 * elapsed time restarts at boot, and the wall clock moves when the user or the network sets it (ADR 0008).
 */
data class TimerAnchor(val elapsedRealtime: Duration, val wallClock: Instant)
