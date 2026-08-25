package dev.upyet.history.ui

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.OccurrenceOutcome
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryGroupingTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 24)

    private fun occurrence(id: Long, scheduledFor: Instant, outcome: OccurrenceOutcome) = AlarmOccurrence(
        id = OccurrenceId(id),
        alarmId = AlarmId(1),
        scheduledFor = scheduledFor,
        triggeredAt = null,
        activityVisibleAt = null,
        dismissedAt = null,
        outcome = outcome,
        parentOccurrenceId = null,
    )

    private fun item(id: Long, scheduledFor: Instant, outcome: OccurrenceOutcome) =
        HistoryViewModel.HistoryItem(occurrence(id, scheduledFor, outcome), "Label", emptyList())

    @Test fun dayBucketForTodayYesterdayAndEarlier() {
        assertThat(dayBucketFor(today.atStartOfDay(zone).toInstant(), zone, today)).isEqualTo(HistoryDayBucket.TODAY)
        assertThat(dayBucketFor(today.minusDays(1).atStartOfDay(zone).toInstant(), zone, today)).isEqualTo(HistoryDayBucket.YESTERDAY)
        assertThat(dayBucketFor(today.minusDays(2).atStartOfDay(zone).toInstant(), zone, today)).isEqualTo(HistoryDayBucket.EARLIER)
        assertThat(dayBucketFor(today.minusDays(30).atStartOfDay(zone).toInstant(), zone, today)).isEqualTo(HistoryDayBucket.EARLIER)
    }

    @Test fun groupHistoryItemsByDaySkipsEmptyBucketsAndPreservesOrderWithinEachBucket() {
        val todayItem1 = item(1, today.atTime(8, 0).atZone(zone).toInstant(), OccurrenceOutcome.DISMISSED)
        val todayItem2 = item(2, today.atTime(6, 0).atZone(zone).toInstant(), OccurrenceOutcome.DISMISSED)
        val earlierItem = item(3, today.minusDays(5).atTime(7, 0).atZone(zone).toInstant(), OccurrenceOutcome.DISMISSED)

        val groups = groupHistoryItemsByDay(listOf(todayItem1, todayItem2, earlierItem), zone, today)

        assertThat(groups.map { it.labelRes }).containsExactly(
            HistoryDayBucket.TODAY.labelRes,
            HistoryDayBucket.EARLIER.labelRes,
        ).inOrder()
        assertThat(groups[0].items).containsExactly(todayItem1, todayItem2).inOrder()
        assertThat(groups[1].items).containsExactly(earlierItem)
    }

    @Test fun groupHistoryItemsByDayReturnsNoGroupsForEmptyInput() {
        assertThat(groupHistoryItemsByDay(emptyList(), zone, today)).isEmpty()
    }

    @Test fun filterAllMatchesEveryOutcome() {
        val items = OccurrenceOutcome.entries.mapIndexed { index, outcome -> item(index.toLong(), Instant.EPOCH, outcome) }
        assertThat(filterHistoryItems(items, HistoryFilter.ALL)).containsExactlyElementsIn(items)
    }

    @Test fun filterDismissedMatchesOnlyDismissed() {
        val dismissed = item(1, Instant.EPOCH, OccurrenceOutcome.DISMISSED)
        val snoozed = item(2, Instant.EPOCH, OccurrenceOutcome.SNOOZED)
        assertThat(filterHistoryItems(listOf(dismissed, snoozed), HistoryFilter.DISMISSED)).containsExactly(dismissed)
    }

    @Test fun filterSnoozedMatchesOnlySnoozed() {
        val dismissed = item(1, Instant.EPOCH, OccurrenceOutcome.DISMISSED)
        val snoozed = item(2, Instant.EPOCH, OccurrenceOutcome.SNOOZED)
        assertThat(filterHistoryItems(listOf(dismissed, snoozed), HistoryFilter.SNOOZED)).containsExactly(snoozed)
    }

    @Test fun filterMissedMatchesTimedOutAndInterrupted() {
        val timedOut = item(1, Instant.EPOCH, OccurrenceOutcome.TIMED_OUT)
        val interrupted = item(2, Instant.EPOCH, OccurrenceOutcome.INTERRUPTED)
        val dismissed = item(3, Instant.EPOCH, OccurrenceOutcome.DISMISSED)
        assertThat(filterHistoryItems(listOf(timedOut, interrupted, dismissed), HistoryFilter.MISSED))
            .containsExactly(timedOut, interrupted)
    }
}
