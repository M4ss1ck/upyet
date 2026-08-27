# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- A failed alarm now leaves evidence for the developer. `AlarmLog` events are persisted to an append-only
  file in device-protected storage - not Room, because the alarm path may not touch it and because Direct
  Boot, before the first unlock, is exactly the window an "it never rang" report is about. Two files of
  128 KB bound it, rotated by size and not by age: the user reporting last Tuesday is the one whose
  evidence retention already reclaimed. `AndroidAlarmScheduler` logged nothing at all before this, so
  scheduling now records that an alarm was set, cancelled, denied for want of exact-alarm access, or
  failed - without which a report from the top failure case would have said nothing about the alarm that
  did not ring. Settings gains one row that assembles those events into a diagnostic report - app and
  Android version, phone model, every reliability check, the settings that change alarm behaviour - and
  hands it to the share sheet. Labels, alarm times and evidence file names are redacted where the line is
  written rather than where it is shared, in every build type, so no window exists in which the file holds
  one. See `docs/adr/0004` and `docs/adr/0005`.
- Everything Play asks for that is not the build: a privacy policy published from `docs/site/` via GitHub
  Pages, a Data Safety declaration of "collects no data, shares no data" with the reasoning written down,
  written justifications for camera, `USE_FULL_SCREEN_INTENT` and `USE_EXACT_ALARM`, listing copy, a
  screenshot shot list, and a release procedure in `docs/RELEASING.md`. The permission card on the alarm
  list becomes the Play prominent disclosure rather than gaining a modal in front of it: it now says that
  no audio is captured, that recording is bound to the alarm screen being visible, that retention deletes
  the clips and that recording can be turned off, under a heading that names the moment and above an
  affirmative "Agree & continue". See `docs/adr/0006`.
- Evidence can leave the app through the Android share sheet. The share action on a wake-up sends every
  clip in the chain at once; each segment card can also send its own clip alone. Clips leave as readable
  copies named for their own ring time (`upyet-2026-08-27-1455.mp4`), so a snoozed wake-up arrives as a
  set of files whose names tell the snooze story, and a written summary of the wake-up rides along in the
  share text - a bare clip proves nothing to whoever receives it. A FileProvider scoped to a cache
  directory carries the copies out; the evidence directory itself is never exposed, the app still declares
  no `INTERNET` permission, and there is deliberately no export-to-storage path. Before the first share
  ever, a one-time explainer says the one thing a user cannot work out alone: retention will never reclaim
  that copy, and deleting the wake-up here will not remove it. See `docs/adr/0003`.
- History answers "how am I doing" instead of only "what happened". A card above the list shows wake-ups
  answered on the first ring as *n of m*, the snoozes and the minutes they cost, and the wake-ups missed
  entirely, over a window of 7 days, 30 days or all time that survives a restart. All-time is answerable
  because retention expires clips, never the record that the alarm rang. The figures ignore the filter
  chips - the filter answers "show me these", the stats answer a question about all of them - and the
  denominator leaves out in-flight rings and our own errors, since counting those against the user would
  make the number a lie. The card stays put on an empty window rather than vanishing.
- An alarm can be vibrate-only or fully silent. A Sound switch in the editor turns the ringtone off
  without discarding it: the ringtone row stays visible with the sound the user picked, so switching
  sound back on restores their choice rather than dropping to the system default. An alarm with
  neither sound nor vibration is a supported configuration - it matters for a deaf or hard-of-hearing
  user, and for a shared bedroom - and the editor says what it will do rather than blocking it. The
  alarm list marks a silent alarm with a muted icon carrying a content description, so the fact
  reaches a screen reader too, and the ringing screen says "Vibration only" or "Silent alarm" so a
  screen that makes no noise reads as working rather than broken

### Fixed
- The ringtone picker offered the system's own "Silent" entry, which returns no URI - and no URI means
  "the system default alarm sound" in this app. Choosing Silent therefore gave you a loud alarm. The
  picker now only picks sounds; silence is the Sound switch's job
- A muted alarm stream no longer forces vibration on an alarm whose sound the user deliberately
  switched off. The override remains for the case it was written for: an alarm that expected to be
  heard, on a device whose alarm stream is at zero

## [0.1.6] - 2026-08-26

### Added
- History rows show a real frame from the clip instead of a play icon. The final available frame is
  selected and written as a JPEG beside its clip in app-private
  evidence storage - never a blob in the database. Extraction happens after the recording is finalized, on
  the path that already outlives the ringing screen, so it cannot delay or block a dismissal; rows recorded
  before this existed backfill themselves the first time they scroll into view. The thumbnail is evidence
  like any other and is deleted with its clip, so retention still means what it says. A row whose clip has
  no frame yet, or never produced one, keeps the icon it has today
- The app knows about alarm volume. A reliability check reports an alarm stream that has been silenced -
  the state in which the alarm makes no sound at all and nothing said so - and deep-links to system sound
  settings, with a separate warning for a stream left so low it may not wake anyone. When the stream is
  silent at ring time the alarm vibrates even if vibration is switched off for it, and the ringing screen
  says why: a silent alarm that also does not buzz fails the only promise the app makes
- The ringtone ramps from a fifth of its volume to full over thirty seconds rather than starting at full
  tilt. The attenuation happens inside the player and never writes to the system alarm stream, so it
  cannot leave the user's volume slider somewhere they did not put it. The last ring of a snooze chain
  does not ramp
- Unanswered alarms re-ring instead of giving up permanently. A timeout still records TIMED_OUT - the
  honest record that nobody answered - and then schedules the next ring the same way a manual snooze
  does. The chain stops when the snooze budget runs out or two hours after the first ring, whichever
  comes first; the two-hour ceiling applies even to an unlimited budget, which is the case it exists for
- A maximum-snoozes setting, default three, options one, three, five or unlimited. The budget is read
  once when the alarm first rings and then carried through the chain as an intent extra and in the
  device-protected mirror, so it survives a reboot mid-chain and works before first unlock, where the
  database is out of reach. The final ring offers no Snooze at all and the one before it is labelled
  "Last snooze"
- History rolls a whole wake-up into one row: the alarm's own time, its final outcome, and "snoozed 2x .
  18 min" instead of three separate rows saying nothing about each other. The row's frame comes from the
  ring the user actually got up on, falling back through the earlier ones. Opening it shows every ring in
  order with its own clips, and deleting it deletes the whole wake-up. Chains are derived from the parent
  link already stored on each occurrence, so nothing changed in the database
- A recurring alarm can be skipped for its next occurrence only, from the overflow menu on its row, and
  un-skipped from the same place until it has passed. The skip is stored as the local date being skipped
  rather than an instant, so it means the same thing across a timezone change or a DST boundary and goes
  inert on its own once the date is behind us. It is mirrored to device-protected storage alongside the
  alarm itself, so a reboot before the skipped morning does not resurrect the ring it was meant to cancel.
  One-time alarms do not offer it: skipping one is what the switch already does
- An upcoming-alarm notification appears some minutes before the next alarm is due, as a silent shade
  entry that says when it is due and whose label it carries, with a single action that either skips the
  next occurrence or turns a one-time alarm off. The lead time is a setting — off, 10 or 30 minutes, 1 or
  2 hours, default 1 hour — and only the earliest enabled alarm is ever shown; a second alarm whose window
  overlaps waits its turn. The notification is inexact on purpose, posted with `setAndAllowWhileIdle`, and
  never touches the alarm-critical scheduling, the Direct Boot mirror, or the exact-alarm permission, so a
  failure to show it can never make the UI claim the alarm is not set, and ringing clears any stale
  entry. It is recomputed on launch, on boot, on unlock and after every ring

### Fixed
- The alarm-volume check now detects a stream that has actually been silenced. It originally tested only
  whether the volume had reached zero, which stock Android never permits: `STREAM_ALARM` has a minimum of
  1 and the platform rejects an index of 0 outright, so the check could not fire and the forced-vibration
  path behind it was unreachable. Found by running it on an emulator rather than reading it

## [0.1.5] - 2026-08-25

UpYet speaks Spanish, and picks its language the way the rest of the phone does. Verified on an
emulator: the whole app in Spanish, the picker round-tripping, and a real alarm ringing with a
Spanish notification posted by the playback service.

### Added
- Spanish. The app now ships `en` and `es`, follows the device locale, and falls back to English for any
  other language. Settings gains a Language row that overrides the app's language independently of the
  system's, on Android 13 and newer; below that the app follows the device locale as before. The override
  is the platform's own per-app language, so it also appears in Android's app-language screen and reaches
  the ringing notification, which the AppCompat backport would not have done for a Compose-only app
  without re-basing the alarm-critical `RingingActivity` on `AppCompatActivity`

### Changed
- A new alarm opens on the next five-minute mark rather than a hardcoded 7:00. The old default was
  wrong for anyone whose alarm was not a morning one, and it made every new alarm a two-step edit.
  A mark less than two minutes out is skipped for the one after it, so an alarm saved as offered
  cannot ring in seconds, and the offsets wrap past midnight instead of landing in the past

## [0.1.4] - 2026-08-25

The first build run on a physical device, which found two things reading the code did not: the ringing
screen never came up, and dismissing an alarm that was recording evidence killed the app. Both were
reproduced on an emulator and the fixes verified there.

### Added
- Snooze and Dismiss actions on the ringing notification. While the device is awake and unlocked the
  platform deliberately shows a heads-up notification instead of launching the full-screen intent, so
  the alarm has to be answerable where it actually appears. Both actions send the playback service the
  same commands the ringing screen sends. An alarm dismissed this way records no evidence, since the
  ringing screen never becomes visible

### Fixed
- The alarm never came to the front. It arrived as a silent entry in the notification shade, with no
  heads-up and no ringing screen, and had to be tapped to reach Snooze and Dismiss. `setSilent(true)`
  on the notification also sets `GROUP_ALERT_SUMMARY` and a silent group key, and the platform then
  suppresses both the heads-up and the full-screen intent. The channel already carries no sound and no
  vibration, so the alarm stays silent without it, and with the device asleep or locked the full-screen
  intent brings the ringing screen up as intended
- The app died as an alarm recording evidence was dismissed. The CameraX callback held the evidence
  segment as it was when the recording was requested: `Start` built the recording copy, persisted it
  and dropped it, so finalizing at dismissal ran against the stale copy and its state check threw on
  the main thread. Each event now hands the next one the segment it left behind, and a recording that
  never reported `Start` is finalized as unavailable instead of throwing, since CameraX can finalize a
  recording that never started and no camera failure may take the alarm down with it

## [0.1.3] - 2026-08-25

Another round of fixes found by using the running app, plus the one bug in this list that was not
cosmetic: a one-time alarm that never switched itself off.

### Fixed
- A one-time alarm came back the next morning. There is no date on a one-time alarm to say it is
  spent, so the reschedule that follows a dismissal rolled it to the same time tomorrow and it
  reappeared as the next alarm. It is switched off once it has rung; repeating alarms roll forward as
  before. Before first unlock the alarm store is out of reach, so the pending occurrence carries it
  and the unlock receiver retires it there
- Picking a minute in the alarm editor threw the dial back to the hour, leaving the minute impossible
  to adjust. The picker was keyed on the alarm's time, so every minute it reported rebuilt it, and a
  fresh picker starts in hour mode. It is now created once from the loaded alarm and owns the value
  from there
- The evidence player pillarboxed portrait clips inside a 16:10 box. It takes the video's own aspect
  ratio now, with the transport below the frame instead of overlaid on it, since the clips are
  portrait and short and the overlay covered most of what there was to see
- History's filter chips squeezed the last chip until "Missed" broke across lines; the four labels are
  wider than a phone. The row scrolls horizontally instead, which holds at any width and in any
  language
- Clock faces sized the full stops in a spelled-out meridiem ("a. m.") as digits, so the marker came
  with two digit-sized dots. Only a full stop between digits counts as part of the face now
- The unchecked switch had no visible thumb in dark mode: the dark scheme gave `outline` and
  `surfaceContainerHighest` the same tone, and Material paints the thumb, the border and the track
  from those two roles. The track drops to the surface tone below it

## [0.1.2] - 2026-08-24

Fixes for what the 0.1.1 redesign got wrong on a real screen. Every item here came from looking at the
running app; none of it was visible from the code.

### Fixed
- Every clock face in the app rendered at running-text size. `ClockText` merged the ambient text style
  *over* the style it was handed, so the body size won — on list rows, the next-alarm card, the editor,
  history and the ringing screen alike. The AM/PM marker was also pinned at 13 sp regardless of the face
  beside it, and is now sized in proportion to it
- Alarm cards clipped the last weekday pills: the switch and overflow had a trailing column of their own,
  taking the width the seven pills needed. Both now sit beside the time, the pills get the full card
  width and size themselves to fit, so all seven survive a narrow screen or a large display scale
- The alarm editor showed the time twice — Material's own editable hour and minute boxes plus a second
  face above them. The second is now a muted read-back below the dial
- The AM/PM selector never appeared: the picker was hardcoded to 24-hour. It follows the device clock
  setting now
- The time picker overflowed instead of shrinking when space was tight, putting the hour box flush
  against the card edge and clipping the dial. It is composed at a fixed design width and scaled to the
  width it is actually given, so it fits by construction rather than by tuned constants
- Digits sat off-centre in the picker's squares and dial numerals off-centre in their selection circles:
  `displayLarge` in the type ramp was the 84 sp ringing clock face, which Material also uses to size
  those boxes. It is now a 44 sp face with a centred line box; the ringing screen asks for `ClockLarge`
  by name and is unaffected
- Settings' reliability summary wrapped mid-sentence, squeezed by the button sharing its row; the button
  moved to its own line
- Settings showed the version and licence twice, in the About row and again in the footer
- The occurrence timeline pushed its step label onto two lines when the timestamp was long; label and
  time are stacked now

### Added
- Alarm overflow menu with Edit and Delete. Editing was previously reachable only by tapping the card

## [0.1.1] - 2026-08-24

The app is now called **UpYet** and has a design system instead of Material 3 defaults.

### Changed
- **BREAKING** — renamed from MyAlarm to UpYet throughout: package and source tree (`dev.upyet`), Gradle
  project, namespace, application id, the Room database class and its on-disk file (`upyet.db`), the debug
  broadcast action, the version property (`upyet.version`) and the release APK name. The new application
  id means an existing install does **not** upgrade in place; the new build installs alongside it
- **BREAKING** — release signing material moves to `~/.config/upyet/android-signing/` and the environment
  variables become `UPYET_ANDROID_KEYSTORE`, `UPYET_ANDROID_KEY_ALIAS`, `UPYET_ANDROID_KEYSTORE_PASSWORD`.
  Move an existing `~/.config/my-alarm/android-signing/` across, or the script generates a fresh key and
  release APKs stop matching earlier ones
- Dynamic colour is no longer used. The app keeps its own palette (ink `#1B1E4B`, signal blue `#2563FF`,
  coral `#FF6B6B`, mist `#F2F4F8`) in both light and dark themes, so an alarm recognised by its colour at
  6:40 am is not repainted by the wallpaper
- Every screen rebuilt on the new system: cards, a clock type ramp with tabular figures, 52 dp controls,
  grouped settings rows and status badges that always state an outcome in words, never colour alone
- History is filterable (all / dismissed / snoozed / missed) and grouped by day
- Occurrence detail shows a "what happened" timeline — scheduled, rang, screen appeared, dismissed —
  in place of a flat list of timestamps
- Reliability leads with a summary that says plainly that a blocked camera stops the evidence, not the
  alarm, rather than implying the alarm itself is broken
- Settings gains a reliability status card and three grouped sections

### Added
- UpYet eye-and-clock mark as the launcher icon, themed (monochrome) icon and status-bar icon
- Next-alarm card on the alarm list, with a countdown that refreshes while the screen is open
- Empty state for a first run with no alarms
- Alarm rows show their recurrence as weekday pills
- Pure, unit-tested helpers for next-alarm selection, history day grouping and history filtering

### Fixed
- Alarm could not be created or saved: the editor was a non-scrolling column taller than a phone screen,
  so Save and Cancel were clipped off the bottom. They now live in a fixed bottom bar and the form scrolls
- Editing an existing alarm showed 07:00 instead of its real time, because the time picker was remembered
  before the stored alarm had loaded (saving then overwrote the alarm's time)
- The seven weekday chips were clipped on narrow screens; they wrap now
- Settings could not be scrolled, hiding controls below the fold on short screens
- The ringing screen's Snooze and Dismiss could be pushed off screen by the camera preview or a long label;
  they are now pinned outside the scrolling area
- The ringtone name is resolved off the main thread; reading the media store during composition could
  stall the first frame of the editor
- The ringing screen's date comes from the view model's `TimeProvider` rather than `LocalDate.now()` inside
  a composable, so it is testable like the rest of the clock

### Added in the 0.1.0 line, before this release
- GPL-3.0-or-later license
- Default snooze duration setting and an app-version row in Settings
- Compose regression test asserting Save stays reachable in a small viewport

### Known gaps
- No custom typeface: the brand faces are not redistributable here and downloadable fonts would mean a
  network round trip in an app that deliberately has none. The brand is carried by the mark, the palette
  and a weight-and-tracking ramp on the platform sans
- Still no end-to-end verification on real hardware; the redesigned screens have never been rendered on a
  device or emulator. Instrumented tests compile but have not been run

## [0.1.0] - 2026-08-24

First installable baseline. Everything below is compile-verified, unit-tested and lint-clean; the alarm,
lock-screen and camera paths have **not** yet been exercised on a physical device.

### Added
- Exact alarm scheduling with `AlarmManager.setAlarmClock()`, one occurrence per scheduled alarm, with
  deterministic PendingIntent identity and idempotent schedule/cancel
- Local-time recurrence rules (one-time, daily, selected weekdays) covering DST gaps, DST overlaps and
  timezone changes, with a pure `java.time` calculator under unit test
- `AlarmPlaybackService` (mediaPlayback foreground service) owning ringtone, vibration, wake lock, the
  alarm notification and the ringing session, independent of any Activity
- Full-screen-intent alarm notification opening `RingingActivity` over the keyguard, with no dismiss
  action that bypasses the recording experience
- Front-camera silent video evidence via CameraX, bound to the visible ringing Activity, modelled as one
  or more segments per occurrence with timestamps taken from real `VideoRecordEvent`s
- Every camera, storage, permission and Direct Boot failure recorded as a machine-readable evidence
  status; dismissal is never blocked
- Snooze scheduling a real exact alarm and linking the snoozed occurrence to its parent
- Direct Boot support: device-protected alarm mirror, ringing/snooze/reschedule before first unlock, and
  reconciliation of occurrences into Room on user unlock
- Reboot, time-change, timezone-change, package-replacement and force-stop recovery through rescheduling
- Room persistence for alarms, occurrences and evidence metadata; videos in app-private `noBackupFilesDir`
  with opaque names; DataStore-backed settings
- Alarm list, alarm editor (time, label, recurrence, vibration, snooze, ringtone, evidence toggle),
  history with segment playback, occurrence deletion including files, settings and a reliability screen
- Evidence retention (1 / 7 / 30 days or forever) with cleanup that never touches an active recording
- In-context notification and camera permission onboarding
- Debug-only adb hook to schedule an alarm N seconds ahead
- Signed release builds through `scripts/build-android-release.sh`

### Known gaps
- No end-to-end verification on real hardware yet; see the matrix in `docs/TESTING.md`
- Instrumented tests compile but have never been executed (no device or emulator available)
- UI is functional but visually unpolished; the release build's R8 configuration is unproven at runtime
