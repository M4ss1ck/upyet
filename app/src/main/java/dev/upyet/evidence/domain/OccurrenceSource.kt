package dev.upyet.evidence.domain

/**
 * What rang. An alarm occurrence keeps everything (snooze chains, wake-ups, stats); a timer occurrence
 * is one showing of the timer ringing screen and never snoozes or counts as a wake-up (AGENTS.md §2, ADR 0010).
 */
enum class OccurrenceSource {
    ALARM,
    TIMER,
}
