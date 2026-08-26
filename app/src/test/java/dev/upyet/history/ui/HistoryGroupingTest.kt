package dev.upyet.history.ui

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.OccurrenceChain
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.buildOccurrenceChains
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryGroupingTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 24)

    private fun occurrence(id: Long, scheduledFor: Instant, outcome: OccurrenceOutcome, parentId: Long? = null) = AlarmOccurrence(
        id = OccurrenceId(id),
        alarmId = AlarmId(1),
        scheduledFor = scheduledFor,
        triggeredAt = null,
        activityVisibleAt = null,
        dismissedAt = null,
        outcome = outcome,
        parentOccurrenceId = parentId?.let { OccurrenceId(it) },
    )

    private fun chainOf(vararg occurrences: AlarmOccurrence): OccurrenceChain {
        val chains = buildOccurrenceChains(occurrences.toList())
        // When single chain expected, return that.
        return if (occurrences.size == 1) chains.first() else chains.first { it.root.id == occurrences[0].id }
    }

    private fun item(id: Long, scheduledFor: Instant, outcome: OccurrenceOutcome) =
        HistoryViewModel.HistoryItem(chainOf(occurrence(id, scheduledFor, outcome)), "Label", emptyMap())

    private fun itemForChain(chain: OccurrenceChain) = HistoryViewModel.HistoryItem(chain, "Label", emptyMap())

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

    @Test fun groupHistoryItemsByDayGroupsChainThatCrossesMidnightByRoot() {
        // Chain rooted at 23:50 today, but its last link is after midnight. It must still bucket as TODAY.
        val root = occurrence(1, today.atTime(23, 50).atZone(zone).toInstant(), OccurrenceOutcome.SNOOZED)
        val snoozed = occurrence(2, today.plusDays(1).atTime(0, 5).atZone(zone).toInstant(), OccurrenceOutcome.DISMISSED, parentId = 1)
        val chain = buildOccurrenceChains(listOf(root, snoozed)).first()
        val chainItem = itemForChain(chain)
        val groups = groupHistoryItemsByDay(listOf(chainItem), zone, today)
        assertThat(groups).hasSize(1)
        assertThat(groups[0].labelRes).isEqualTo(HistoryDayBucket.TODAY.labelRes)
        assertThat(groups[0].items).containsExactly(chainItem)
    }

    @Test fun filterAllMatchesEveryOutcome() {
        val items = OccurrenceOutcome.entries.mapIndexed { index, outcome -> item(index.toLong(), Instant.EPOCH, outcome) }
        assertThat(filterHistoryItems(items, HistoryFilter.ALL)).containsExactlyElementsIn(items)
    }

    @Test fun filterDismissedMatchesOnlyDismissed() {
        val dismissed = item(1, Instant.EPOCH, OccurrenceOutcome.DISMISSED)
        // A chain whose final outcome is SNOOZED should not match DISMISSED filter.
        val snoozed = item(2, Instant.EPOCH, OccurrenceOutcome.SNOOZED)
        assertThat(filterHistoryItems(listOf(dismissed, snoozed), HistoryFilter.DISMISSED)).containsExactly(dismissed)
    }

    @Test fun filterSnoozedMatchesOnlySnoozed() {
        // SNOOZED filter means the wake-up contains a snooze (chain.containsSnooze), not finalOutcome == SNOOZED.
        val withoutSnooze = item(1, Instant.EPOCH, OccurrenceOutcome.DISMISSED)
        val root = occurrence(10, Instant.parse("2026-08-24T07:00:00Z"), OccurrenceOutcome.SNOOZED)
        val final = occurrence(11, Instant.parse("2026-08-24T07:09:00Z"), OccurrenceOutcome.DISMISSED, parentId = 10)
        val withSnoozeChain = buildOccurrenceChains(listOf(root, final)).first()
        val withSnooze = itemForChain(withSnoozeChain)
        assertThat(filterHistoryItems(listOf(withoutSnooze, withSnooze), HistoryFilter.SNOOZED)).containsExactly(withSnooze)
    }

    @Test fun filterMissedMatchesTimedOutAndInterrupted() {
        val timedOut = item(1, Instant.EPOCH, OccurrenceOutcome.TIMED_OUT)
        val interrupted = item(2, Instant.EPOCH, OccurrenceOutcome.INTERRUPTED)
        val dismissed = item(3, Instant.EPOCH, OccurrenceOutcome.DISMISSED)
        assertThat(filterHistoryItems(listOf(timedOut, interrupted, dismissed), HistoryFilter.MISSED))
            .containsExactly(timedOut, interrupted)
    }
}
