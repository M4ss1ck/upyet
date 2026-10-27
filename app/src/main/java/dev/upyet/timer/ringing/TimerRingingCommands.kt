package dev.upyet.timer.ringing

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.playback.TimerPlaybackService
import javax.inject.Inject

/** What the timer ringing screen asks `TimerPlaybackService` to do; the screen itself never touches the ringing. */
interface TimerRingingCommands {
    fun stop(id: TimerId)

    fun addMinute(id: TimerId)

    fun stopAll()
}

class ServiceTimerRingingCommands @Inject constructor(@ApplicationContext private val context: Context) : TimerRingingCommands {
    override fun stop(id: TimerId) = send(TimerPlaybackService.stopIntent(context, id))

    override fun addMinute(id: TimerId) = send(TimerPlaybackService.addMinuteIntent(context, id))

    override fun stopAll() = send(TimerPlaybackService.stopAllIntent(context))

    // startForegroundService keeps working even if the service was recreated between the ring and the tap.
    private fun send(intent: Intent) {
        ContextCompat.startForegroundService(context, intent)
    }
}
