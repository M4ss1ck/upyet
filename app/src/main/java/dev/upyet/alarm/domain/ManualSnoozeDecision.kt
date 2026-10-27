package dev.upyet.alarm.domain

/**
 * What a user's Snooze tap on a ringing alarm may do. Off is checked first: a ring with no snooze is not a
 * budget question, and an exhausted budget must not be reported as the reason for a rejected Off tap.
 */
object ManualSnoozeDecision {
    enum class Verdict { ACCEPT, REJECT_OFF, DENY_BUDGET_EXHAUSTED }

    fun decide(snooze: SnoozePolicy, budget: SnoozeBudget): Verdict = when {
        !snooze.allowsSnooze -> Verdict.REJECT_OFF
        budget.isExhausted -> Verdict.DENY_BUDGET_EXHAUSTED
        else -> Verdict.ACCEPT
    }
}
