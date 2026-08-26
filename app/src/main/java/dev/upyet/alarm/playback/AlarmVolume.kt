package dev.upyet.alarm.playback

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmVolume @Inject constructor(@ApplicationContext private val context: Context) {
    private fun audioManager(): AudioManager = context.getSystemService(AudioManager::class.java)

    fun currentVolume(): Int = audioManager().getStreamVolume(AudioManager.STREAM_ALARM)

    fun maxVolume(): Int = audioManager().getStreamMaxVolume(AudioManager.STREAM_ALARM)

    fun isSilent(): Boolean = currentVolume() <= 0

    fun settingsIntent(): Intent = Intent(Settings.ACTION_SOUND_SETTINGS)
}
