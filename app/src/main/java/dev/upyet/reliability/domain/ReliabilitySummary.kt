package dev.upyet.reliability.domain

/**
 * How many checks need the user's attention. Only a BLOCKED check counts: a WARNING is
 * informational and never stops the alarm ringing, so counting it here would make a routine
 * warning (like battery optimisation) look as urgent as a camera permission that is actually
 * blocking evidence.
 */
data class ReliabilitySummary(val blockedCount: Int, val totalCount: Int) {
    val allGood: Boolean get() = blockedCount == 0
}

/** Reduces a full evaluation down to what the settings card and the reliability hero both show. */
fun List<ReliabilityCheck>.summarize(): ReliabilitySummary =
    ReliabilitySummary(blockedCount = count { it.status == ReliabilityStatus.BLOCKED }, totalCount = size)
