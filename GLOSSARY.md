# UpYet

UpYet is a full replacement for the phone's own Clock app: alarms, world clock, timer and stopwatch,
whose distinguishing feature is video evidence of the user dismissing the alarm. This file is the shared
vocabulary: the words the code, the docs and the agents working here
all use for the same thing. It is a glossary, not a spec — architecture lives in `AGENTS.md` and
`docs/ARCHITECTURE.md`, and decisions live in `docs/adr/`.

## Language

### Alarms and ringing

**Alarm**:
A user-configured rule for when to wake them — a time, a recurrence, and how it should ring. An alarm is
not an event; it produces occurrences.
_Avoid_: Reminder, timer, event

**Occurrence**:
One ringing of an alarm, or one recorded ringing of a timer, and the thing history records. An occurrence
has a source, alarm or timer: alarm occurrences keep snooze chains, wake-ups and stats, while timer
occurrences never snooze. A timer ringing with **Timer evidence** off leaves no occurrence. A
snooze produces a new alarm occurrence chained to the one it came from.
_Avoid_: Instance, trigger, ring

**Wake-up**:
One alarm occurrence plus every snooze occurrence chained to it: the whole story of one alarm going off,
from the first ring to the dismissal, timeout or interruption that ended it. Someone who snoozed four
times had one wake-up, not five. History rows, sharing and stats all count wake-ups, never individual
occurrences, and only alarms form them: a timer occurrence is never part of a wake-up.
_Avoid_: Session, event, group

**Ringing**:
The state between an alarm occurrence firing and the user dismissing it: wake lock held, notification
posted, full-screen intent launched, and whatever sound and vibration the alarm is configured for. A timer
ringing is described separately below, because it snoozes nothing and ends in Stop or **+1 min**.

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

**Voice alarm**:
A one-time alarm that another app or an assistant created without opening its editor, and that is deleted
once its occurrence is dismissed. Deleting it keeps that occurrence and its evidence in History. An alarm
the user made, or a voice alarm the user later edits, is never a voice alarm: it stays on the list.
_Avoid_: Temporary alarm, quick alarm

### Clock tools

UpYet is a full replacement for the phone's own Clock app: alarms, plus the three tools below.

**Clock tool**:
The World clock, the Timer or the Stopwatch: any UpYet feature other than alarms. The umbrella exists
because rules about ringing, notifications and evidence now mostly cover alarms and clock tools together,
and would otherwise turn into a list of three.
_Avoid_: Utility, widget, feature

**Clock-tool ongoing notification**:
The single low-importance notification a running timer or a running stopwatch shows, driven by a chronometer
from a stored anchor so that no service has to run and nothing has to be recomputed. It is not a **Ringing**
notification: it carries no full-screen intent, and its actions mirror the timer list or the stopwatch controls
rather than ending a ring.
_Avoid_: Foreground notification, running notification (neither is a foreground service)

**World clock**:
A user-chosen list of places, each showing its current local time and its offset from the user's own.
_Avoid_: Global time, time zones (a zone is what a place resolves to, not the feature)

**Gazetteer**:
The bundled, offline dataset behind World clock search and the map on that screen: place names in English and
Spanish, coordinates, population, country, and the IANA zone each place resolves to, plus the land outline the
map draws. It ships inside the APK as a generated asset and is never downloaded at build time or at runtime; a
place names a zone, while a zone's own label still comes from the platform.
_Avoid_: City list, database, dataset

**Data sources**:
The Settings › About screen, and the repository `NOTICE` file, where the third-party data UpYet ships is
credited: place data from GeoNames, map outlines from Natural Earth, and the IANA time zone database. The
GeoNames credit is a licence condition rather than a courtesy, which is why it appears in the app and in the
published privacy page and not only in the repository.
_Avoid_: Licences, credits

**Timer**:
A countdown from a chosen duration that rings when it reaches zero. A timer rings through the same
reliable path as an alarm, and its ring can record evidence and leave an **Occurrence** in history, but
only when its **Timer evidence** is on.
_Avoid_: Countdown, alarm (an alarm is a wall-clock rule; a timer is a duration)

**Timer evidence**:
The per-timer switch that decides whether that timer records while it rings. It takes the global "Record
when a timer rings" switch at creation, and is the reason a timer can appear in History at all.
_Avoid_: Recording, camera toggle

**Timer run state**:
One of the five states a timer is in: `Idle`, `Running`, `Paused`, `Ringing` or `Missed`. Idle is a timer at
its full length, waiting to be started; Running and Paused hold the time left. A timer is never described as
*expired* or *reset*: those are DeskClock's names for two of these states, and here they read as an error
and as an action.
_Avoid_: Expired, Reset, status

**Timer ringing**:
A timer that has reached zero and is sounding, the state between reaching zero and being stopped or given
**+1 min**. Several timers can be ringing at once, sharing one sound, one wake lock and one ringing
screen. It borrows the alarm's sound and vibration machinery, and it records like an alarm whenever at
least one ringing timer has **Timer evidence** on, but it is not an alarm **Ringing**: nothing is snoozed,
nothing is dismissed and no wake-up forms.
_Avoid_: Expired, alarm ringing

**Missed timer**:
A timer that was Running and was found more than a minute past its end the next time the app looked, after a
reboot, an unlock or a launch, meaning it ended while the phone could not ring it. A missed timer never rings
late; it waits silently under a low-importance notification until the user resets or deletes it.
_Avoid_: Late timer, expired timer

**+1 min**:
The control that gives a timer another minute. On a Running or Paused timer it adds 60 seconds to the
remaining time; on a ringing or missed timer it stops that timer and restarts it from 60 seconds. Idle
timers do not offer it.
_Avoid_: Snooze (a snooze belongs to an alarm occurrence), extend

**Stopwatch**:
A count-up from the moment the user starts it, with laps. It never rings.
_Avoid_: Chronometer, timer

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
