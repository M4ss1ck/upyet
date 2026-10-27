package dev.upyet.evidence.domain

import java.time.Instant

enum class StatsWindow(val days: Int?) {
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    ALL_TIME(null),
}

data class WakeUpStats(val firstTry: Int, val finished: Int, val snoozes: Int, val snoozedMinutes: Int, val missed: Int)

fun wakeUpStats(chains: List<OccurrenceChain>, window: StatsWindow, now: Instant): WakeUpStats {
    // AGENTS.md §2 invariant 11: wake-up stats are alarm-only. A timer chain never snoozes, so counting it would
    // inflate "finished" and "first try" without being a wake-up at all.
    val alarmChains = chains.filter { it.root.source == OccurrenceSource.ALARM }
    val inWindow = if (window.days == null) {
        alarmChains
    } else {
        val cutoff = now.minusSeconds(window.days.toLong() * 86_400L)
        alarmChains.filter { !it.root.scheduledFor.isBefore(cutoff) }
    }
    val finished = inWindow.count { it.finalOutcome != OccurrenceOutcome.RINGING && it.finalOutcome != OccurrenceOutcome.ERROR }
    val firstTry = inWindow.count { it.finalOutcome == OccurrenceOutcome.DISMISSED && it.snoozeCount == 0 }
    val snoozes = inWindow.sumOf { it.snoozeCount }
    val missed = inWindow.count { it.finalOutcome == OccurrenceOutcome.TIMED_OUT || it.finalOutcome == OccurrenceOutcome.INTERRUPTED }

    var snoozedMillis = 0L
    for (chain in inWindow) {
        val links = chain.links
        for (i in 0 until links.size - 1) {
            val earlier = links[i]
            val later = links[i + 1]
            if (earlier.outcome == OccurrenceOutcome.SNOOZED) {
                val diff = later.scheduledFor.toEpochMilli() - earlier.scheduledFor.toEpochMilli()
                if (diff > 0) snoozedMillis += diff
            }
        }
    }
    val snoozedMinutes = (snoozedMillis / 60_000L).toInt()

    return WakeUpStats(
        firstTry = firstTry,
        finished = finished,
        snoozes = snoozes,
        snoozedMinutes = snoozedMinutes,
        missed = missed,
    )
}
