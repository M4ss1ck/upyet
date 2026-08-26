package dev.upyet.alarm.domain

import java.time.Duration
import java.time.Instant

object AutoSnoozeDecision {
    /** Wall-clock ceiling on a chain of pure timeouts. A user manually snoozing is present and choosing; a chain nobody answered
     * has failed by this point. */
    val MAX_AUTO_SNOOZE_WINDOW: Duration = Duration.ofHours(2)

    /**
     * Whether an unanswered ring should re-ring.
     * [chainStartedAt] is when the MAIN alarm of this chain first triggered.
     */
    fun shouldAutoSnooze(budget: SnoozeBudget, chainStartedAt: Instant, now: Instant): Boolean {
        if (budget.isExhausted) return false
        if (Duration.between(chainStartedAt, now) >= MAX_AUTO_SNOOZE_WINDOW) return false
        return true
    }
}
