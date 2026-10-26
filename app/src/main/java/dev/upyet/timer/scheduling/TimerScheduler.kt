package dev.upyet.timer.scheduling

/**
 * Programs the one exact alarm timers keep (ADR 0008, ADR 0009). [arm] replaces whatever was armed before,
 * so calling it again with the same [TimerAlarm] changes nothing; [cancel] is just as idempotent.
 */
interface TimerScheduler {
    fun arm(alarm: TimerAlarm): TimerArmResult

    fun cancel()
}

sealed interface TimerArmResult {
    data object Armed : TimerArmResult

    /** No exact-alarm access. Nothing was armed: a timer is never downgraded to an inexact alarm (AGENTS.md §6). */
    data object ExactAlarmsUnavailable : TimerArmResult

    data class Failed(val cause: Throwable) : TimerArmResult
}
