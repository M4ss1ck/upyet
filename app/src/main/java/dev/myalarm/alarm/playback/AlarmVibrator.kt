package dev.myalarm.alarm.playback

import android.content.Context
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
    fun start() {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
    }
    fun stop() {
        vibrator.cancel()
    }
}
