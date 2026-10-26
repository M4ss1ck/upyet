package dev.upyet.timer.playback

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.scheduling.TimerRingingHandoff
import javax.inject.Inject

/**
 * A validated expiry, already stored as Ringing, goes to [TimerPlaybackService], the only thing that may ring a
 * timer (AGENTS.md §8a). Called from `TimerReceiver`'s exact-alarm delivery, which is what authorises the
 * `mediaPlayback` foreground-service start, including before first unlock and inside the boot grace window.
 */
class ServiceTimerRingingHandoff @Inject constructor(@ApplicationContext private val context: Context) : TimerRingingHandoff {
    override fun ring(timerId: TimerId) {
        ContextCompat.startForegroundService(context, TimerPlaybackService.ringIntent(context, timerId))
    }
}
