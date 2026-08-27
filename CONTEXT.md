# UpYet

An Android replacement alarm clock whose distinguishing feature is video evidence of the user dismissing
the alarm. This file is the shared vocabulary: the words the code, the docs and the agents working here
all use for the same thing. It is a glossary, not a spec — architecture lives in `AGENTS.md` and
`docs/ARCHITECTURE.md`, and decisions live in `docs/adr/`.

## Language

### Alarms and ringing

**Alarm**:
A user-configured rule for when to wake them — a time, a recurrence, and how it should ring. An alarm is
not an event; it produces occurrences.
_Avoid_: Reminder, timer, event

**Occurrence**:
One firing of an alarm at one wall-clock instant, and the thing history records. A snooze produces a new
occurrence chained to the one it came from.
_Avoid_: Instance, trigger, ring

**Ringing**:
The state between an occurrence firing and the user dismissing it: wake lock held, notification posted,
full-screen intent launched, and whatever sound and vibration the alarm is configured for.

**Evidence**:
The silent front-camera video recorded while the ringing screen is visible, plus its metadata. Evidence
is local-only and never blocks dismissal.
_Avoid_: Recording, footage, proof

### Silence

Three distinct concepts share the word "silent" in everyday speech. They are kept apart here because two
are properties the user chose and one is a device condition they probably did not.

**Vibrate-only alarm**:
An alarm with `soundEnabled = false` and `vibrationEnabled = true`. It rings, wakes the screen and
records evidence; it just makes no sound.

**Silent alarm**:
An alarm with both `soundEnabled = false` and `vibrationEnabled = false`. It rings, wakes the screen and
records evidence, makes no sound and does not buzz. A deliberate, supported configuration, not a broken alarm.
_Avoid_: Muted alarm, disabled alarm

**Muted alarm stream**:
The *device* condition in which `STREAM_ALARM` is at or near zero, so an alarm that expected to be heard
will not be. A property of the phone, never of an alarm.
_Avoid_: Silent (reserved for the alarm property above), muted alarm

### Reliability

**Reliability check**:
A device-level condition that could stop an alarm from working — exact-alarm access, notification
permission, a muted alarm stream — surfaced with a deep link to the system settings screen that fixes it.
Reliability checks describe the device, never an individual alarm.

**Skip next**:
A one-shot suppression of a recurring alarm's next occurrence, after which the alarm resumes on its own.
Distinct from disabling an alarm, which is indefinite and requires the user to remember to undo it.
