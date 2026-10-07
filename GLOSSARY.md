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

**Wake-up**:
One occurrence plus every snooze occurrence chained to it — the whole story of one alarm going off, from
the first ring to the dismissal, timeout or interruption that ended it. Someone who snoozed four times had
one wake-up, not five. History rows, sharing and stats all count wake-ups, never individual occurrences.
_Avoid_: Session, event, group

**Ringing**:
The state between an occurrence firing and the user dismissing it: wake lock held, notification posted,
full-screen intent launched, and whatever sound and vibration the alarm is configured for.

**Evidence**:
The silent front-camera video recorded while the ringing screen is visible, plus its metadata. Evidence
is local-only and never blocks dismissal.
_Avoid_: Recording, footage, proof

**Skip next**:
A one-shot suppression of a recurring alarm's next occurrence, after which the alarm resumes on its own.
A skip lapses the moment the occurrence it suppressed would have rung, not at the end of that day: from
then on the alarm is no longer skipped, and a new skip targets the occurrence after. One-time alarms
cannot be skipped; for them skipping and turning off are the same act.
_Avoid_: Snooze (a snooze delays an occurrence that already rang), pause

**Turn off**:
Making an alarm stop producing occurrences until the user turns it back on. Indefinite, unlike a skip,
which is why turning a recurring alarm off asks whether the user meant to skip only the next occurrence.
Turning off discards any skip and any pending snooze: an alarm turned back on rings at its next occurrence.
_Avoid_: Disable (fine in code, not in the UI), delete

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

### Sharing

**Shared copy**:
A copy of a wake-up's evidence that the user has deliberately sent out of the app through the Android share
sheet. It leaves with a readable name and a written summary of the wake-up it came from, because a bare clip
proves nothing to whoever receives it. Once shared it is beyond the app's reach: retention never expires it,
and deleting the occurrence does not delete it.
_Avoid_: Export, backup, upload

### Diagnostics

**Log event**:
One recorded fact about what the alarm machinery did — that an alarm was scheduled, that it fired, that the
camera failed to start. A time, a name and a few fields, never a sentence. Log events are recorded whether
or not anyone will ever read them, and they never carry an alarm label or an evidence file name.
_Avoid_: Log line, trace, breadcrumb

**Diagnostic report**:
The single shareable artifact a user sends when their alarm did not work: the recent log events plus the
context needed to read them — app and Android version, phone model, and the state of every reliability
check. Its reader is a developer, not the user, which is why the app never displays one.
_Avoid_: Log export, crash report, bug report

**Prominent disclosure**:
The in-app explanation of what the camera records and when, shown before the camera permission is ever
requested and requiring a deliberate tap to proceed. A Play requirement with a specific shape, met by the
permission card on the alarm list — not a general term for any screen that mentions the camera.
_Avoid_: Consent screen, onboarding, permission rationale

### Stats

**Stats**:
The rolled-up answer to "how am I doing" over a chosen window: first-try wake-ups as a fraction of the
finished ones, the snoozes and minutes lost to them, and the wake-ups missed entirely. Stats describe
wake-ups, never individual occurrences, and they never follow the history filter — the filter answers
"show me these", the stats answer a question about all of them.

**First-try wake-up**:
A wake-up the user dismissed on the first ring, having snoozed none. The one outcome the stats treat as
good.
_Avoid_: Clean wake, success, streak

**Stats window**:
The stretch of time the stats describe — the last 7 days, the last 30 days, or all of it. It bounds the
stats alone; the history list below is unaffected by it. All-time is answerable because occurrences outlive
their evidence: retention expires clips, never the record that the alarm rang.
