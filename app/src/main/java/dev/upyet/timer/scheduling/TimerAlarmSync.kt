package dev.upyet.timer.scheduling

import dev.upyet.core.time.TimerClock
import dev.upyet.timer.domain.Timer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** The missed-timer notice as the shade holds it (`timer_missed`, id 1103). */
interface TimerMissedNotices {
    fun isShowing(): Boolean

    fun post(missed: List<Timer>)

    fun clear()
}

/** What one re-arm did: the alarm it chose (null: none was running, so it cancelled) and how arming went. */
data class TimerSyncResult(val alarm: TimerAlarm?, val arm: TimerArmResult?)

/**
 * Brings the one exact alarm and the missed notice in line with the timers, after every change. Direct-Boot
 * safe: it reads no store itself, the caller passes [read] for whichever store is authoritative right now.
 *
 * One lock, and [read] runs inside it: two re-arms racing must not leave the older snapshot armed last,
 * because a delivery armed from it would be dropped as stale and the real end would never ring.
 */
@Singleton
class TimerAlarmSync @Inject constructor(
    private val scheduler: TimerScheduler,
    private val notices: TimerMissedNotices,
    private val clock: TimerClock,
) {
    private val lock = Mutex()

    suspend fun sync(newlyMissed: Boolean = false, read: suspend () -> List<Timer>): TimerSyncResult = lock.withLock {
        val timers = read()
        val alarm = TimerAlarm.next(timers, clock.now())
        val arm = if (alarm == null) {
            scheduler.cancel()
            null
        } else {
            scheduler.arm(alarm)
        }
        when (val notice = MissedNotice.decide(timers, newlyMissed, notices.isShowing())) {
            is MissedNotice.Post -> notices.post(notice.missed)
            MissedNotice.Clear -> notices.clear()
            MissedNotice.Leave -> Unit
        }
        TimerSyncResult(alarm, arm)
    }
}
