package dev.upyet.alarm.ringing

import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import org.junit.Test

class RingingNotesTest {
    @Test
    fun accidentReturnsVolumeSilent() {
        assertThat(ringingNoteRes(soundEnabled = true, vibrationEnabled = false, mutedAlarmStream = true))
            .isEqualTo(R.string.ringing_volume_silent)
    }

    @Test
    fun accidentReturnsVolumeSilentEvenWhenVibrationEnabled() {
        assertThat(ringingNoteRes(soundEnabled = true, vibrationEnabled = true, mutedAlarmStream = true))
            .isEqualTo(R.string.ringing_volume_silent)
    }

    @Test
    fun vibrateOnlyReturnsVibrationOnly() {
        assertThat(ringingNoteRes(soundEnabled = false, vibrationEnabled = true, mutedAlarmStream = false))
            .isEqualTo(R.string.ringing_vibration_only)
    }

    @Test
    fun fullySilentReturnsSilentAlarm() {
        assertThat(ringingNoteRes(soundEnabled = false, vibrationEnabled = false, mutedAlarmStream = false))
            .isEqualTo(R.string.ringing_silent_alarm)
    }

    @Test
    fun normalReturnsNull() {
        assertThat(ringingNoteRes(soundEnabled = true, vibrationEnabled = false, mutedAlarmStream = false))
            .isNull()
    }

    @Test
    fun normalWithVibrationReturnsNull() {
        assertThat(ringingNoteRes(soundEnabled = true, vibrationEnabled = true, mutedAlarmStream = false))
            .isNull()
    }

    @Test
    fun mutedStreamWithSoundDisabledDoesNotReturnAccidentNote() {
        assertThat(ringingNoteRes(soundEnabled = false, vibrationEnabled = true, mutedAlarmStream = true))
            .isEqualTo(R.string.ringing_vibration_only)
    }
}
