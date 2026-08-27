package dev.upyet.alarm.ringing

import androidx.annotation.StringRes
import dev.upyet.R

@StringRes
fun ringingNoteRes(soundEnabled: Boolean, vibrationEnabled: Boolean, mutedAlarmStream: Boolean): Int? = when {
    mutedAlarmStream && soundEnabled -> R.string.ringing_volume_silent
    !soundEnabled && vibrationEnabled -> R.string.ringing_vibration_only
    !soundEnabled && !vibrationEnabled -> R.string.ringing_silent_alarm
    else -> null
}
