package dev.upyet.evidence.domain

import dev.upyet.timer.domain.TimerId

/**
 * A snapshot of one recording-on timer that a timer occurrence covered. It is text in the occurrence row, never a
 * foreign key, because deleting a timer keeps its past evidence (AGENTS.md §10, ADR 0010).
 */
data class TimerAttribution(val timerId: TimerId, val label: String)
