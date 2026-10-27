package dev.upyet.alarm.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ManualSnoozeDecisionTest {
    @Test
    fun offRejectsEvenWithUnlimitedBudget() {
        assertThat(ManualSnoozeDecision.decide(SnoozePolicy.Off, SnoozeBudget(SnoozeBudget.UNLIMITED)))
            .isEqualTo(ManualSnoozeDecision.Verdict.REJECT_OFF)
    }

    @Test
    fun offIsReportedAsOffNotAsExhaustedBudget() {
        assertThat(ManualSnoozeDecision.decide(SnoozePolicy.Off, SnoozeBudget(0)))
            .isEqualTo(ManualSnoozeDecision.Verdict.REJECT_OFF)
    }

    @Test
    fun afterWithExhaustedBudgetIsDenied() {
        assertThat(ManualSnoozeDecision.decide(SnoozePolicy.After(9), SnoozeBudget(0)))
            .isEqualTo(ManualSnoozeDecision.Verdict.DENY_BUDGET_EXHAUSTED)
    }

    @Test
    fun afterWithBudgetLeftIsAccepted() {
        assertThat(ManualSnoozeDecision.decide(SnoozePolicy.After(9), SnoozeBudget(2)))
            .isEqualTo(ManualSnoozeDecision.Verdict.ACCEPT)
        assertThat(ManualSnoozeDecision.decide(SnoozePolicy.After(9), SnoozeBudget(SnoozeBudget.UNLIMITED)))
            .isEqualTo(ManualSnoozeDecision.Verdict.ACCEPT)
    }
}
