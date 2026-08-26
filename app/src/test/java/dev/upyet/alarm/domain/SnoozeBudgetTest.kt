package dev.upyet.alarm.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SnoozeBudgetTest {
    @Test
    fun consumeDecrementsChain() {
        val start = SnoozeBudget(3)
        val after1 = start.consume()
        val after2 = after1.consume()
        val after3 = after2.consume()
        assertThat(after1.remaining).isEqualTo(2)
        assertThat(after2.remaining).isEqualTo(1)
        assertThat(after3.remaining).isEqualTo(0)
    }

    @Test
    fun isExhaustedOnlyAtZero() {
        assertThat(SnoozeBudget(3).isExhausted).isFalse()
        assertThat(SnoozeBudget(1).isExhausted).isFalse()
        assertThat(SnoozeBudget(0).isExhausted).isTrue()
    }

    @Test
    fun isLastSnoozeOnlyAtOne() {
        assertThat(SnoozeBudget(3).isLastSnooze).isFalse()
        assertThat(SnoozeBudget(1).isLastSnooze).isTrue()
        assertThat(SnoozeBudget(0).isLastSnooze).isFalse()
    }

    @Test
    fun unlimitedNeverExhaustedNeverLastAndConsumeIsIdentity() {
        val unlimited = SnoozeBudget(SnoozeBudget.UNLIMITED)
        assertThat(unlimited.isUnlimited).isTrue()
        assertThat(unlimited.isExhausted).isFalse()
        assertThat(unlimited.isLastSnooze).isFalse()
        assertThat(unlimited.consume()).isEqualTo(unlimited)
    }

    @Test
    fun consumeAtZeroStaysZero() {
        assertThat(SnoozeBudget(0).consume().remaining).isEqualTo(0)
    }

    @Test
    fun ofUnsetReturnsDefaultMax() {
        assertThat(SnoozeBudget.of(SnoozeBudget.UNSET)).isEqualTo(SnoozeBudget(SnoozeBudget.DEFAULT_MAX))
    }

    @Test
    fun ofUnlimitedIsUnlimited() {
        val budget = SnoozeBudget.of(SnoozeBudget.UNLIMITED)
        assertThat(budget.isUnlimited).isTrue()
    }
}
