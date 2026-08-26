package dev.upyet.alarm.domain

@JvmInline
value class SnoozeBudget(val remaining: Int) {
    val isUnlimited: Boolean get() = remaining == UNLIMITED
    val isExhausted: Boolean get() = !isUnlimited && remaining <= 0
    val isLastSnooze: Boolean get() = !isUnlimited && remaining == 1

    fun consume(): SnoozeBudget = if (isUnlimited) this else SnoozeBudget((remaining - 1).coerceAtLeast(0))

    companion object {
        const val UNLIMITED = -1
        const val DEFAULT_MAX = 3
        const val UNSET = Int.MIN_VALUE

        fun of(value: Int): SnoozeBudget = if (value == UNSET) SnoozeBudget(DEFAULT_MAX) else SnoozeBudget(value)
    }
}
