# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- Alarm could not be created or saved: the editor was a non-scrolling column taller than a phone screen,
  so Save and Cancel were clipped off the bottom. They now live in a fixed bottom bar and the form scrolls
- Editing an existing alarm showed 07:00 instead of its real time, because the time picker was remembered
  before the stored alarm had loaded (saving then overwrote the alarm's time)
- The seven weekday chips were clipped on narrow screens; they wrap now
- Settings could not be scrolled, hiding controls below the fold on short screens
- The ringing screen's Snooze and Dismiss could be pushed off screen by the camera preview or a long label;
  they are now pinned outside the scrolling area

### Added
- Default snooze duration setting and an app-version row in Settings
- Compose regression test asserting Save stays reachable in a small viewport

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
