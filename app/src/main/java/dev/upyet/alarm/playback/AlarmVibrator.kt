package dev.upyet.alarm.playback

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmVibrator @Inject constructor(@ApplicationContext context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /**
     * The attributes are not decoration. Without them the platform files the vibration as
     * `USAGE_UNKNOWN`, which Do Not Disturb suppresses and which OEM policy is free to weaken - a Xiaomi
     * 14T on HyperOS 3.0 was observed doing exactly that. `USAGE_ALARM` is exempt from DND and follows the
     * alarm vibration intensity setting, which is the behaviour an alarm clock has to have: the user is
     * asleep, the phone is silenced for the night, and the vibration is the part that still has to happen.
     * These are the same attributes the ringtone plays under.
     */
    fun start() {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0), alarmAttributes)
    }
    fun stop() {
        vibrator.cancel()
    }

    private companion object {
        // vibrate(VibrationEffect, AudioAttributes) has existed since API 21, so minSdk 26 needs no branch.
        val alarmAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
