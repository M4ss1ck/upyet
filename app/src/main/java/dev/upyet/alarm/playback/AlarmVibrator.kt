package dev.upyet.alarm.playback

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
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
     * The ringtone plays under the same `USAGE_ALARM`.
     */
    fun start() {
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            // API 33 deprecated this overload in favour of VibrationAttributes, which older releases lack.
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, alarmAudioAttributes)
        }
    }
    fun stop() {
        vibrator.cancel()
    }

    private companion object {
        // Only for API 26-32. On 33+ the platform converts these to VibrationAttributes USAGE_ALARM itself,
        // so passing that directly is the same vibration without the deprecated overload.
        val alarmAudioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
