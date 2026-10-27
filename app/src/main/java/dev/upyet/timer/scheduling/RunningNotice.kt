package dev.upyet.timer.scheduling

import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerRunState
import java.time.Instant

/**
 * What the running-timer notice (`clock_tool_ongoing`, id 1102; ADR 0012) shows after timers change: the Running
 * timer that ends first, or nothing. Pure, so the choice is unit-tested; [TimerAlarmSync] applies it.
 */
sealed interface RunningNotice {
    /** [timer] is the Running timer that ends first; [alsoRunning] how many others are Running. */
    data class Show(val timer: Timer, val endsAt: Instant, val alsoRunning: Int) : RunningNotice

    data object Cancel : RunningNotice

    companion object {
        /**
         * Only a Running timer counts: Paused, Ringing, Missed and Idle never show here. Ties on [endsAt] go to
         * the smaller id, so the same timers always name the same timer.
         */
        fun decide(timers: List<Timer>): RunningNotice {
            val running = timers
                .filter { it.runState is TimerRunState.Running }
                .mapNotNull { timer -> timer.endsAt?.let { timer to it } }
            val first = running.minWithOrNull(compareBy<Pair<Timer, Instant>> { it.second }.thenBy { it.first.id.value })
                ?: return Cancel
            return Show(first.first, first.second, alsoRunning = running.size - 1)
        }
    }
}
