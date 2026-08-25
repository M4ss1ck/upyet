# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
