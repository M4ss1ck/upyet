package dev.myalarm.history.ui

import androidx.annotation.StringRes
import dev.myalarm.R
import dev.myalarm.evidence.domain.OccurrenceOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The filter chips above the history list. "Missed" folds together the two ways an alarm goes unanswered. */
enum class HistoryFilter {
    ALL,
    DISMISSED,
    SNOOZED,
    MISSED,
    ;

    fun matches(outcome: OccurrenceOutcome): Boolean = when (this) {
        ALL -> true
        DISMISSED -> outcome == OccurrenceOutcome.DISMISSED
        SNOOZED -> outcome == OccurrenceOutcome.SNOOZED
        MISSED -> outcome == OccurrenceOutcome.TIMED_OUT || outcome == OccurrenceOutcome.INTERRUPTED
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

/** Keeps only the items whose outcome matches the selected filter, preserving order. */
fun filterHistoryItems(items: List<HistoryViewModel.HistoryItem>, filter: HistoryFilter): List<HistoryViewModel.HistoryItem> =
    items.filter { filter.matches(it.occurrence.outcome) }

/**
 * Buckets items into Today / Yesterday / Earlier, dropping empty buckets. Input is expected newest
 * first (as the repository already returns it); each bucket keeps that relative order.
 */
fun groupHistoryItemsByDay(items: List<HistoryViewModel.HistoryItem>, zone: ZoneId, today: LocalDate): List<HistoryGroup> =
    HistoryDayBucket.entries.mapNotNull { bucket ->
        val matching = items.filter { dayBucketFor(it.occurrence.scheduledFor, zone, today) == bucket }
        if (matching.isEmpty()) null else HistoryGroup(bucket.labelRes, matching)
    }
