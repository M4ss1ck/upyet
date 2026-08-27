package dev.upyet.evidence.domain

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import org.junit.Test
import java.time.Instant

class WakeUpStatsTest {
    private val now = Instant.parse("2026-08-24T10:00:00Z")

    private fun occurrence(
        id: Long,
        scheduledFor: Instant,
        outcome: OccurrenceOutcome = OccurrenceOutcome.DISMISSED,
        parentId: Long? = null,
        dismissedAt: Instant? = null,
        triggeredAt: Instant? = null,
    ) = AlarmOccurrence(
        id = OccurrenceId(id),
        alarmId = AlarmId(1),
        scheduledFor = scheduledFor,
        triggeredAt = triggeredAt,
        activityVisibleAt = null,
        dismissedAt = dismissedAt,
        outcome = outcome,
        parentOccurrenceId = parentId?.let { OccurrenceId(it) },
    )

    @Test fun dismissedWithNoSnoozeCountsAsFirstTry() {
        val single = occurrence(1, now.minusSeconds(3600), OccurrenceOutcome.DISMISSED)
        val chain = OccurrenceChain(listOf(single))
        val stats = wakeUpStats(listOf(chain), StatsWindow.ALL_TIME, now)
        assertThat(stats.firstTry).isEqualTo(1)
        assertThat(stats.finished).isEqualTo(1)
        assertThat(stats.snoozes).isEqualTo(0)
        assertThat(stats.snoozedMinutes).isEqualTo(0)
        assertThat(stats.missed).isEqualTo(0)
    }

    @Test fun chainWithTwoSnoozesCountsSnoozesAndMinutesNotFirstTry() {
        val root = occurrence(1, now.minusSeconds(3600 * 2), OccurrenceOutcome.SNOOZED)
        val snooze1 = occurrence(2, now.minusSeconds(3600 * 2 - 9 * 60), OccurrenceOutcome.SNOOZED, parentId = 1)
        val final = occurrence(3, now.minusSeconds(3600 * 2 - 18 * 60), OccurrenceOutcome.DISMISSED, parentId = 2)
        val chain = OccurrenceChain(listOf(root, snooze1, final))
        val stats = wakeUpStats(listOf(chain), StatsWindow.ALL_TIME, now)
        assertThat(stats.firstTry).isEqualTo(0)
        assertThat(stats.finished).isEqualTo(1)
        assertThat(stats.snoozes).isEqualTo(2)
        assertThat(stats.snoozedMinutes).isEqualTo(18)
        assertThat(stats.missed).isEqualTo(0)
    }

    @Test fun snoozedMinutesSumsIntervalsAndExcludesFinalDismissalGap() {
        // root SNOOZED at T, final DISMISSED at T+9min but dismissedAt 6min later — should still be 9.
        val scheduledRoot = Instant.parse("2026-08-24T07:00:00Z")
        val scheduledFinal = Instant.parse("2026-08-24T07:09:00Z")
        val dismissedAt = Instant.parse("2026-08-24T07:15:00Z")
        val root = occurrence(1, scheduledRoot, OccurrenceOutcome.SNOOZED)
        val final = occurrence(2, scheduledFinal, OccurrenceOutcome.DISMISSED, parentId = 1, dismissedAt = dismissedAt)
        val chain = OccurrenceChain(listOf(root, final))
        val stats = wakeUpStats(listOf(chain), StatsWindow.ALL_TIME, now)
        assertThat(stats.snoozedMinutes).isEqualTo(9)
        // Also verify intermediate gaps summed correctly: 9 + 5 = 14.
        val root2 = occurrence(10, Instant.parse("2026-08-24T07:00:00Z"), OccurrenceOutcome.SNOOZED)
        val mid = occurrence(11, Instant.parse("2026-08-24T07:09:00Z"), OccurrenceOutcome.SNOOZED, parentId = 10)
        val final2 = occurrence(12, Instant.parse("2026-08-24T07:14:00Z"), OccurrenceOutcome.DISMISSED, parentId = 11)
        val chain2 = OccurrenceChain(listOf(root2, mid, final2))
        val stats2 = wakeUpStats(listOf(chain2), StatsWindow.ALL_TIME, now)
        assertThat(stats2.snoozedMinutes).isEqualTo(14)
    }

    @Test fun snoozedMinutesRoundingDown() {
        // 90 seconds should be 1 minute (rounding down).
        val root = occurrence(1, Instant.parse("2026-08-24T07:00:00Z"), OccurrenceOutcome.SNOOZED)
        val final = occurrence(2, Instant.parse("2026-08-24T07:01:30Z"), OccurrenceOutcome.DISMISSED, parentId = 1)
        val chain = OccurrenceChain(listOf(root, final))
        val stats = wakeUpStats(listOf(chain), StatsWindow.ALL_TIME, now)
        assertThat(stats.snoozedMinutes).isEqualTo(1)
    }

    @Test fun ringingAndErrorExcludedFromFinishedButErrorNotFirstTry() {
        val ringing = OccurrenceChain(listOf(occurrence(1, now.minusSeconds(1000), OccurrenceOutcome.RINGING)))
        val error = OccurrenceChain(listOf(occurrence(2, now.minusSeconds(900), OccurrenceOutcome.ERROR)))
        val dismissed = OccurrenceChain(listOf(occurrence(3, now.minusSeconds(800), OccurrenceOutcome.DISMISSED)))
        val stats = wakeUpStats(listOf(ringing, error, dismissed), StatsWindow.ALL_TIME, now)
        assertThat(stats.finished).isEqualTo(1)
        assertThat(stats.firstTry).isEqualTo(1)
        // Error chain contributes to snoozes? It has snoozeCount 0, so 0. But missed should be 0.
        assertThat(stats.missed).isEqualTo(0)
        // Ensure error not counted as firstTry even if snoozeCount == 0 — firstTry only DISMISSED.
        val errorOnly = wakeUpStats(listOf(error), StatsWindow.ALL_TIME, now)
        assertThat(errorOnly.firstTry).isEqualTo(0)
        assertThat(errorOnly.finished).isEqualTo(0)
    }

    @Test fun timedOutAndInterruptedBothCountedAsMissed() {
        val timedOut = OccurrenceChain(listOf(occurrence(1, now.minusSeconds(1000), OccurrenceOutcome.TIMED_OUT)))
        val interrupted = OccurrenceChain(listOf(occurrence(2, now.minusSeconds(900), OccurrenceOutcome.INTERRUPTED)))
        val dismissed = OccurrenceChain(listOf(occurrence(3, now.minusSeconds(800), OccurrenceOutcome.DISMISSED)))
        val stats = wakeUpStats(listOf(timedOut, interrupted, dismissed), StatsWindow.ALL_TIME, now)
        assertThat(stats.missed).isEqualTo(2)
        assertThat(stats.finished).isEqualTo(3)
    }

    @Test fun chainOlderThanWindowExcludedBySevenDaysButIncludedByAllTime() {
        val old = OccurrenceChain(listOf(occurrence(1, now.minusSeconds(8 * 86_400L), OccurrenceOutcome.DISMISSED)))
        val recent = OccurrenceChain(listOf(occurrence(2, now.minusSeconds(2 * 86_400L), OccurrenceOutcome.DISMISSED)))
        val sevenStats = wakeUpStats(listOf(old, recent), StatsWindow.SEVEN_DAYS, now)
        assertThat(sevenStats.finished).isEqualTo(1)
        assertThat(sevenStats.firstTry).isEqualTo(1)
        val allStats = wakeUpStats(listOf(old, recent), StatsWindow.ALL_TIME, now)
        assertThat(allStats.finished).isEqualTo(2)
        assertThat(allStats.firstTry).isEqualTo(2)
    }

    @Test fun windowBoundaryInclusive() {
        val cutoff = now.minusSeconds(7 * 86_400L)
        val atCutoff = OccurrenceChain(listOf(occurrence(1, cutoff, OccurrenceOutcome.DISMISSED)))
        val beforeCutoff = OccurrenceChain(listOf(occurrence(2, cutoff.minusSeconds(1), OccurrenceOutcome.DISMISSED)))
        val stats = wakeUpStats(listOf(atCutoff, beforeCutoff), StatsWindow.SEVEN_DAYS, now)
        assertThat(stats.finished).isEqualTo(1)
    }

    @Test fun thirtyDaysWindowFiltering() {
        val old = OccurrenceChain(listOf(occurrence(1, now.minusSeconds(31 * 86_400L), OccurrenceOutcome.DISMISSED)))
        val recent = OccurrenceChain(listOf(occurrence(2, now.minusSeconds(10 * 86_400L), OccurrenceOutcome.DISMISSED)))
        val stats = wakeUpStats(listOf(old, recent), StatsWindow.THIRTY_DAYS, now)
        assertThat(stats.finished).isEqualTo(1)
    }

    @Test fun emptyInputYieldsAllZeros() {
        val stats = wakeUpStats(emptyList(), StatsWindow.SEVEN_DAYS, now)
        assertThat(stats).isEqualTo(WakeUpStats(0, 0, 0, 0, 0))
        val statsAll = wakeUpStats(emptyList(), StatsWindow.ALL_TIME, now)
        assertThat(statsAll).isEqualTo(WakeUpStats(0, 0, 0, 0, 0))
    }

    @Test fun windowContainingNothingYieldsZeros() {
        val old = OccurrenceChain(listOf(occurrence(1, now.minusSeconds(10 * 86_400L), OccurrenceOutcome.DISMISSED)))
        val stats = wakeUpStats(listOf(old), StatsWindow.SEVEN_DAYS, now)
        assertThat(stats).isEqualTo(WakeUpStats(0, 0, 0, 0, 0))
    }

    @Test fun snoozesSumOverAllInWindowWithNoExclusions() {
        // Even RINGING and ERROR chains contribute snoozeCount (though they have 0, test sum)
        val chainWithSnooze = OccurrenceChain(
            listOf(
                occurrence(1, now.minusSeconds(1000), OccurrenceOutcome.SNOOZED),
                occurrence(2, now.minusSeconds(500), OccurrenceOutcome.DISMISSED, parentId = 1),
            ),
        )
        val ringingChain = OccurrenceChain(listOf(occurrence(3, now.minusSeconds(400), OccurrenceOutcome.RINGING)))
        val stats = wakeUpStats(listOf(chainWithSnooze, ringingChain), StatsWindow.ALL_TIME, now)
        assertThat(stats.snoozes).isEqualTo(1)
    }
}
