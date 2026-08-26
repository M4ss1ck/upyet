package dev.upyet.history.ui

import androidx.annotation.StringRes
import dev.upyet.R
import dev.upyet.evidence.domain.OccurrenceChain
import dev.upyet.evidence.domain.OccurrenceOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The filter chips above the history list. "Missed" folds together the two ways an alarm goes unanswered.
 * Filters describe a whole wake-up [OccurrenceChain], not a single occurrence. SNOOZED means the chain
 * contains a snooze rather than ending in one.
 */
enum class HistoryFilter {
    ALL,
    DISMISSED,
    SNOOZED,
    MISSED,
    ;

    fun matches(chain: OccurrenceChain): Boolean = when (this) {
        ALL -> true
        DISMISSED -> chain.finalOutcome == OccurrenceOutcome.DISMISSED
        SNOOZED -> chain.containsSnooze
        MISSED -> chain.finalOutcome == OccurrenceOutcome.TIMED_OUT || chain.finalOutcome == OccurrenceOutcome.INTERRUPTED
    }
}

/** The three day buckets a history row can fall into, relative to today in the current zone. */
enum class HistoryDayBucket(@StringRes val labelRes: Int) {
    TODAY(R.string.history_today),
    YESTERDAY(R.string.history_yesterday),
    EARLIER(R.string.history_earlier),
}

/** A labelled group of rows, ready for the composable to lay out without doing any date math itself. */
data class HistoryGroup(@StringRes val labelRes: Int, val items: List<HistoryViewModel.HistoryItem>)

/** Which bucket a scheduled time falls into, given today's local date in that same zone. */
fun dayBucketFor(scheduledFor: Instant, zone: ZoneId, today: LocalDate): HistoryDayBucket {
    val date = scheduledFor.atZone(zone).toLocalDate()
    return when {
        date == today -> HistoryDayBucket.TODAY
        date == today.minusDays(1) -> HistoryDayBucket.YESTERDAY
        else -> HistoryDayBucket.EARLIER
    }
}

/** Keeps only the items whose chain matches the selected filter, preserving order. */
fun filterHistoryItems(items: List<HistoryViewModel.HistoryItem>, filter: HistoryFilter): List<HistoryViewModel.HistoryItem> =
    items.filter { filter.matches(it.chain) }

/**
 * Buckets items into Today / Yesterday / Earlier, dropping empty buckets. Input is expected newest
 * first (as the repository already returns it); each bucket keeps that relative order.
 * A chain that snoozes past midnight stays in the day its alarm was SET for (root.scheduledFor).
 */
fun groupHistoryItemsByDay(items: List<HistoryViewModel.HistoryItem>, zone: ZoneId, today: LocalDate): List<HistoryGroup> =
    HistoryDayBucket.entries.mapNotNull { bucket ->
        val matching = items.filter { dayBucketFor(it.chain.root.scheduledFor, zone, today) == bucket }
        if (matching.isEmpty()) null else HistoryGroup(bucket.labelRes, matching)
    }
