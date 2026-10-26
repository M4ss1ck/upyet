package dev.upyet.timer.playback

import android.content.Context
import dev.upyet.alarm.playback.AlarmSoundPlayer
import dev.upyet.alarm.playback.AlarmVibrator

/**
 * The timer's one sound and vibration, built from the two classes the timer path shares with the alarm path
 * (AGENTS.md §5a). Shared classes, never shared instances: the Hilt `@Singleton` [AlarmSoundPlayer] holds one
 * `MediaPlayer`, and its `start` releases the previous one, so a timer ringing through it would silence a ringing
 * alarm. The vibrator is built on the service's own context for the same reason: the platform cancels by the
 * calling `Vibrator`'s token and a service context gets its own, so a timer's cancel should not reach an alarm's
 * vibration. That last part is reasoned from the platform source, not yet observed on a device.
 *
 * No volume ramp: a timer is something the user is waiting for, not someone asleep (ADR 0008 offers none).
 */
class TimerSoundAndVibration(context: Context) : TimerRingingOutput {
    private val sound = AlarmSoundPlayer(context.applicationContext)
    private val vibrator = AlarmVibrator(context)

    override fun start(ringtoneUri: String?) {
        sound.start(ringtoneUri, ramp = false)
        vibrator.start()
    }

    override fun stop() {
        sound.stop()
        vibrator.stop()
    }
}
