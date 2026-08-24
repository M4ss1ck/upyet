package dev.myalarm.alarm.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.provider.Settings
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.myalarm.core.logging.AlarmLog
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmSoundPlayer @Inject constructor(@ApplicationContext private val context: Context) {
    private var player: MediaPlayer? = null

    /** A content URI may be unreadable before first unlock, so alarm and system defaults are fallbacks. */
    fun start(uri: String?) {
        stop()
        val candidates =
            listOfNotNull(
                uri,
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)?.toString(),
                Settings.System.DEFAULT_ALARM_ALERT_URI.toString(),
            )
        for (candidate in candidates) {
            try {
                val mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder().setUsage(
                            AudioAttributes.USAGE_ALARM,
                        ).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    )
                    setDataSource(context, candidate.toUri())
                    isLooping = true
                    prepare()
                    start()
                }
                player = mediaPlayer
                return
            } catch (error: IOException) {
                AlarmLog.event("alarm_error", "error" to error.javaClass.simpleName)
            } catch (error: IllegalStateException) {
                AlarmLog.event("alarm_error", "error" to error.javaClass.simpleName)
            } catch (error: SecurityException) {
                AlarmLog.event("alarm_error", "error" to error.javaClass.simpleName)
            }
        }
    }

    fun stop() {
        player?.release()
        player = null
    }
}
