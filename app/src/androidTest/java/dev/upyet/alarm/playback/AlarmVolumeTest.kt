package dev.upyet.alarm.playback

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The volume check is the difference between an alarm that rings and one that is silent while the app
 * says everything is fine. Only a real AudioManager knows what the alarm stream is set to and what it
 * will even accept, and what it accepts is the whole subtlety: stock Android gives `STREAM_ALARM` a
 * minimum of 1 and rejects an index of 0, so a check written against volume alone never fires. These
 * tests exist to keep that discovery from being undone.
 */
@RunWith(AndroidJUnit4::class)
class AlarmVolumeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val alarmVolume = AlarmVolume(context)
    private val original = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

    @After fun restoreTheUsersVolume() {
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
    }

    /**
     * The reason `isSilent` cannot be a volume test alone. If a platform ever does allow 0 this fails,
     * which is the correct outcome: the assumption behind the mute check would have changed.
     */
    @Test fun theAlarmStreamHasANonZeroMinimumOnThisPlatform() {
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)

        val floor = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

        assertThat(floor).isEqualTo(minimumAlarmVolume())
    }

    @Test fun theAlarmStreamAtItsFloorIsReportedAsBarelyAudible() {
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, minimumAlarmVolume(), 0)

        assertThat(alarmVolume.isBarelyAudible()).isTrue()
        assertThat(alarmVolume.isSilent()).isEqualTo(minimumAlarmVolume() <= 0)
    }

    @Test fun aFullAlarmStreamIsNeitherSilentNorBarelyAudible() {
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmVolume.maxVolume(), 0)

        assertThat(alarmVolume.isSilent()).isFalse()
        assertThat(alarmVolume.isBarelyAudible()).isFalse()
        assertThat(alarmVolume.currentVolume()).isEqualTo(alarmVolume.maxVolume())
    }

    /** Silent and barely-audible are different states, and nothing may report both. */
    @Test fun silentAndBarelyAudibleAreMutuallyExclusiveAtEveryVolume() {
        for (volume in 0..alarmVolume.maxVolume()) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)

            assertThat(alarmVolume.isSilent() && alarmVolume.isBarelyAudible()).isFalse()
        }
    }

    @Test fun anUnmutedStreamIsNotReportedAsMuted() {
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmVolume.maxVolume(), 0)

        assertThat(alarmVolume.isMuted()).isFalse()
        assertThat(alarmVolume.isSilent()).isFalse()
    }

    @Test fun theMaximumIsAPositiveVolumeTheRampCanScaleAgainst() {
        assertThat(alarmVolume.maxVolume()).isGreaterThan(0)
    }

    @Test fun theSettingsIntentResolvesOnThisDevice() {
        assertThat(alarmVolume.settingsIntent().resolveActivity(context.packageManager)).isNotNull()
    }

    /** There is no public getter for a stream's minimum below API 28, so it is discovered by asking. */
    private fun minimumAlarmVolume(): Int {
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        return audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
    }
}
