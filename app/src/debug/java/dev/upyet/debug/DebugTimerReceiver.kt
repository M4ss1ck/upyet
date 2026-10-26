package dev.upyet.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.time.TimerClock
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRepository
import dev.upyet.timer.domain.TimerTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

/**
 * Debug-only, beside [DebugAlarmReceiver]: starts a timer N seconds long through the repository, exactly as the
 * Timer tab will, so the mirror write and the re-arm are the production path (`docs/TESTING.md` §5).
 */
@AndroidEntryPoint
class DebugTimerReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: TimerRepository

    @Inject lateinit var clock: TimerClock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TIMER) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = clock.now()
                val seconds = intent.getIntExtra(EXTRA_SECONDS, DEFAULT_SECONDS).coerceAtLeast(1).toLong()
                val created = Timer.create(
                    id = TimerId(0),
                    length = Duration.ofSeconds(seconds),
                    label = "Debug timer",
                    ringtoneUri = null,
                    evidenceEnabled = intent.getBooleanExtra(EXTRA_EVIDENCE, false),
                    createdAt = now.wallClock,
                )
                val started = (created.start(now) as TimerTransition.Applied).timer
                val id = repository.upsert(started)
                AlarmLog.event("debug_timer_started", "timerId" to id.value, "seconds" to seconds)
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        const val ACTION_TIMER = "dev.upyet.debug.TIMER"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_EVIDENCE = "evidence"
        const val DEFAULT_SECONDS = 60
    }
}
