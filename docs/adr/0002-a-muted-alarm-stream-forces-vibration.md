# A muted alarm stream forces vibration on, even when the alarm has vibration switched off

When `AlarmVolume.isSilent()` reports the device's alarm stream at or near zero, `AlarmPlaybackService`
starts the vibrator regardless of the alarm's `vibrationEnabled` setting. This deliberately overrides a
user setting, which is normally the wrong thing to do, so it is recorded here to stop it being "fixed".

The override applies only in the **accident** case: the alarm has `soundEnabled = true`, so the user
expected to hear it, and the device is muted for reasons that usually have nothing to do with this alarm
(a night-time volume-down, an OEM profile, a game). The alarm would otherwise fail silently at the one
moment it exists for, and a buzz is the last line of defence.

It explicitly does **not** apply in the **intent** case: when `soundEnabled = false` the user has already
decided this alarm makes no sound, the stream volume is irrelevant to it, and their `vibrationEnabled`
choice is honoured exactly as set — with no forced vibration, no ringing-screen banner and no
`alarm_volume_zero` log event.
