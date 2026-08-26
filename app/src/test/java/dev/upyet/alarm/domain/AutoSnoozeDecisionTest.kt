package dev.upyet.alarm.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AutoSnoozeDecisionTest {
    private val chainStart = Instant.parse("2026-08-24T10:00:00Z")

    @Test
    fun exhaustedBudgetNeverAutoSnoozes() {
        val budget = SnoozeBudget(0)
        assertThat(AutoSnoozeDecision.shouldAutoSnooze(budget, chainStart, chainStart)).isFalse()
        assertThat(AutoSnoozeDecision.shouldAutoSnooze(budget, chainStart, chainStart.plusSeconds(60))).isFalse()
    }

    @Test
    fun budgetWithRemainingAutoSnoozesInsideWindow() {
        val budget = SnoozeBudget(3)
        val now = chainStart.plus(Duration.ofMinutes(30))
        assertThat(AutoSnoozeDecision.shouldAutoSnooze(budget, chainStart, now)).isTrue()
    }

    @Test
    fun exactlyAtTwoHourBoundaryDoesNotAutoSnooze() {
        val budget = SnoozeBudget(3)
        val now = chainStart.plus(Duration.ofHours(2))
        assertThat(AutoSnoozeDecision.shouldAutoSnooze(budget, chainStart, now)).isFalse()
    }

    @Test
    fun pastTwoHoursDoesNotAutoSnooze() {
        val budget = SnoozeBudget(3)
        val now = chainStart.plus(Duration.ofHours(2)).plusSeconds(1)
        assertThat(AutoSnoozeDecision.shouldAutoSnooze(budget, chainStart, now)).isFalse()
    }

    @Test
    fun unlimitedAutoSnoozesInsideWindow() {
        val budget = SnoozeBudget(SnoozeBudget.UNLIMITED)
        val now = chainStart.plus(Duration.ofMinutes(90))
        assertThat(AutoSnoozeDecision.shouldAutoSnooze(budget, chainStart, now)).isTrue()
    }

    @Test
    fun unlimitedDoesNotAutoSnoozePastWindow() {
        val budget = SnoozeBudget(SnoozeBudget.UNLIMITED)
        val now = chainStart.plus(Duration.ofHours(2)).plusSeconds(1)
        assertThat(AutoSnoozeDecision.shouldAutoSnooze(budget, chainStart, now)).isFalse()
    }

    @Test
    fun unlimitedExactlyAtBoundaryDoesNotAutoSnooze() {
        val budget = SnoozeBudget(SnoozeBudget.UNLIMITED)
        val now = chainStart.plus(Duration.ofHours(2))
        assertThat(AutoSnoozeDecision.shouldAutoSnooze(budget, chainStart, now)).isFalse()
    }

    @Test
    fun chainStartedAtEqualToNowAutoSnoozes() {
        val budget = SnoozeBudget(1)
        assertThat(AutoSnoozeDecision.shouldAutoSnooze(budget, chainStart, chainStart)).isTrue()
    }
}
