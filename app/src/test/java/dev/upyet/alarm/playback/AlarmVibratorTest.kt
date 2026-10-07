package dev.upyet.alarm.playback

import android.content.Context
import android.os.VibrationAttributes
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The vibration must be filed as an alarm on every supported release: anything else is suppressed by
 * Do Not Disturb, which is exactly the state a sleeping user's phone is in.
 */
@RunWith(RobolectricTestRunner::class)
class AlarmVibratorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * API 32 takes the AudioAttributes overload, which the platform converts to VibrationAttributes itself.
     * Asserting the converted value is what proves the API 33+ branch, which passes VibrationAttributes
     * directly, produces the same vibration as before.
     */
    @Test
    @Config(sdk = [32])
    fun beforeApi33TheAlarmAudioAttributesBecomeAnAlarmVibration() {
        AlarmVibrator(context).start()

        assertLastVibrationIsAnAlarm()
    }

    @Test
    @Config(sdk = [33])
    fun fromApi33VibratesWithAlarmVibrationAttributes() {
        AlarmVibrator(context).start()

        assertLastVibrationIsAnAlarm()
    }

    @Test
    @Config(sdk = [33])
    fun stopCancelsTheVibration() {
        val alarmVibrator = AlarmVibrator(context)
        alarmVibrator.start()

        alarmVibrator.stop()

        assertThat(shadowOf(context.getSystemService(Vibrator::class.java)).isVibrating).isFalse()
    }

    private fun assertLastVibrationIsAnAlarm() {
        val shadow = shadowOf(context.getSystemService(Vibrator::class.java))
        assertThat(shadow.isVibrating).isTrue()
        // The shadow types this as Object so it still loads on releases that predate VibrationAttributes.
        val attributes = shadow.vibrationAttributesFromLastVibration as VibrationAttributes
        assertThat(attributes.usage).isEqualTo(VibrationAttributes.USAGE_ALARM)
    }
}
