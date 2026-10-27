package dev.upyet.evidence.domain

enum class OccurrenceOutcome {
    RINGING,
    DISMISSED,
    SNOOZED,
    INTERRUPTED,
    TIMED_OUT,
    ERROR,

    /** A timer occurrence the user ended from the ringing screen, by Stop or +1 min. */
    STOPPED,
}
