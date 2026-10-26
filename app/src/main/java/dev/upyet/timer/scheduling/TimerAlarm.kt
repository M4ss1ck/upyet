package dev.upyet.timer.scheduling

import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import java.time.Duration
import java.time.Instant

/**
 * The one exact alarm timers keep (ADR 0008): which timer it is for, the [endsAt] its delivery is validated
 * against, and when it fires on the elapsed-realtime clock. Plain Kotlin, so the choice is unit-tested.
 */
data class TimerAlarm(val timerId: TimerId, val endsAt: Instant, val triggerAtElapsed: Duration) {
    companion object {
        /**
         * The alarm for whichever Running timer reaches zero first at [now], or null when none is running.
         * An overdue timer fires at [now], which is the immediate exact alarm of the boot grace window. A tie
         * goes to the lower id, so re-arming the same timers twice arms the same alarm.
         */
        fun next(timers: List<Timer>, now: TimerAnchor): TimerAlarm? = timers
            .mapNotNull { timer -> timer.endsAt?.let { TimerAlarm(timer.id, it, now.elapsedRealtime.plus(timer.remaining(now))) } }
            .minWithOrNull(compareBy<TimerAlarm> { it.triggerAtElapsed }.thenBy { it.timerId.value })
    }
}
