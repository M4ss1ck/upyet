package dev.upyet.alarm.playback

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the alarm stream is actually set to. Read-only by design: the ramp attenuates inside the player,
 * and nothing here ever writes a volume the user did not choose.
 */
@Singleton
class AlarmVolume @Inject constructor(@ApplicationContext private val context: Context) {
    private fun audioManager(): AudioManager = context.getSystemService(AudioManager::class.java)

    fun currentVolume(): Int = audioManager().getStreamVolume(AudioManager.STREAM_ALARM)

    fun maxVolume(): Int = audioManager().getStreamMaxVolume(AudioManager.STREAM_ALARM)

    fun isMuted(): Boolean = audioManager().isStreamMute(AudioManager.STREAM_ALARM)

    /**
     * The alarm will make no sound at all.
     *
     * The stream volume alone does not answer this. On stock Android `STREAM_ALARM` has a minimum of 1
     * and the platform rejects an index of 0 outright, so a volume test on its own is unreachable there -
     * verified on an API 34 emulator, which refuses `--set 0` with "should be in [1..7]". Muting is the
     * route that actually silences an alarm stream. The volume test is still kept, because the minimum is
     * a per-device value and OEM builds are free to allow 0.
     */
    fun isSilent(): Boolean = isMuted() || currentVolume() <= 0

    /**
     * Audible, but so quiet it may not wake anyone - the "or near zero" half of the problem, and the half
     * that actually occurs on stock Android where the slider cannot reach zero at all.
     */
    fun isBarelyAudible(): Boolean = !isSilent() && currentVolume() * BARELY_AUDIBLE_DIVISOR <= maxVolume()

    fun settingsIntent(): Intent = Intent(Settings.ACTION_SOUND_SETTINGS)

    private companion object {
        /** At or below a quarter of the maximum counts as barely audible. */
        const val BARELY_AUDIBLE_DIVISOR = 4
    }
}
