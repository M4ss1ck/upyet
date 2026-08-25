package dev.upyet.alarm.domain

import java.time.Duration
import java.time.Instant

object SnoozeCalculator {
    fun snoozeAt(now: Instant, minutes: Int): Instant = now.plus(Duration.ofMinutes(minutes.toLong()))
}
