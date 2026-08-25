package dev.upyet.reliability.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReliabilitySummaryTest {
    @Test fun allGoodWhenNothingIsBlocked() {
        val summary = listOf(check(ReliabilityStatus.OK), check(ReliabilityStatus.WARNING)).summarize()

        assertThat(summary.blockedCount).isEqualTo(0)
        assertThat(summary.totalCount).isEqualTo(2)
        assertThat(summary.allGood).isTrue()
    }

    @Test fun onlyBlockedChecksCountAsNeedingAttention() {
        val summary = listOf(
            check(ReliabilityStatus.OK),
            check(ReliabilityStatus.WARNING),
            check(ReliabilityStatus.BLOCKED),
        ).summarize()

        assertThat(summary.blockedCount).isEqualTo(1)
        assertThat(summary.totalCount).isEqualTo(3)
        assertThat(summary.allGood).isFalse()
    }

    @Test fun emptyListIsAllGood() {
        val summary = emptyList<ReliabilityCheck>().summarize()

        assertThat(summary.blockedCount).isEqualTo(0)
        assertThat(summary.totalCount).isEqualTo(0)
        assertThat(summary.allGood).isTrue()
    }

    private fun check(status: ReliabilityStatus) = ReliabilityCheck("id", 0, status, 0)
}
