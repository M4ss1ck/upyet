# AGENTS.md — UpYet

Authoritative engineering contract for every coding agent working in this repository.
Read this file before changing anything. If a request conflicts with the invariants
below, preserve the invariants and document the conflict instead of silently weakening
alarm reliability or platform security.

```text
ALARM RELIABILITY IS MORE IMPORTANT THAN EVIDENCE RECORDING.

The alarm must never depend on the camera, Compose UI, Room database,
or credential-protected storage being available.

Use AlarmManager.setAlarmClock() for real alarm occurrences.

Do not implement alarm scheduling with WorkManager, timers,
coroutine delays, setRepeating(), or ordinary notifications.

AlarmPlaybackService owns ringing.
RingingActivity owns interaction.
CameraX owns evidence while RingingActivity is visible.

Never silently record from the background.

Never bypass Android keyguard security.

Never make inability to record video prevent alarm dismissal.

Never access credential-protected Room storage from a Direct Boot path
before verifying the user is unlocked.
```

## 1. Product definition

UpYet is a replacement alarm-clock application for Android. Its distinguishing
feature is **video evidence of the user dismissing the alarm**: while our own ringing
screen is visible, the front camera records silent video, and the resulting occurrence
answers one question later — *"Did I actually wake up and dismiss that alarm?"*

UpYet is **not** a companion for the system Clock app, a system-wide unlock recorder,
an intruder-selfie app, a background surveillance tool, or a generic reminder app.
The camera runs only during our own visible ringing experience.

## 2. Product invariants

1. A scheduled alarm rings at its wall-clock time even if the UI process was killed,
   the screen is off, the device is locked, the device is in Doze, or the device
   rebooted after scheduling.
2. Ringing (audio, vibration, notification, wake lock) is owned by a foreground
   service and never by an Activity.
3. The alarm is always dismissible. Any camera, storage, database, or UI failure is a
   recorded state, never a blocker and never a crash.
4. Recording happens only while `RingingActivity` is actually visible/resumed.
5. `recordingStartedAt` is set only when CameraX reports the recording actually started.
6. Recurrence is local-time based. Never `previous + 24h`.
7. Scheduling and cancellation are idempotent and use deterministic PendingIntent identity.
8. Evidence is local-only: app-private, non-backed-up storage; metadata in Room; no
   network of any kind.
9. Deleting an occurrence deletes its evidence files. Deleting an alarm does not
   silently delete past evidence.
10. If scheduling fails, the UI must not claim the alarm is active.

## 3. Supported platform

* minSdk 26, targetSdk 36, compileSdk 37 (compileSdk is ahead of targetSdk only because
  current stable AndroidX artifacts require it; runtime behavior remains targetSdk 36).
* Single Gradle application module `:app`.
* JDK 17 language level; Gradle runs on JDK 17+.

## 4. Stack (verified stable on 2026-08-24)

| Concern | Choice |
|---|---|
| Build | AGP 9.3.2 (built-in Kotlin), Gradle 9.7.1, Kotlin 2.3.21, KSP 2.3.11 |
| UI | Jetpack Compose (BOM 2026.08.00), Material 3, Navigation Compose |
| DI | Hilt 2.60.1 |
| Persistence | Room 2.8.4 (metadata), DataStore Preferences 1.2.1 (settings) |
| Camera | CameraX 1.6.1 (`camera-video`, `camera-compose`) |
| Playback of evidence | Media3 1.10.1 |
| Time | `java.time` only |
| Format/lint | Spotless + ktlint 1.8.0 (Kotlin official style = ktlint `intellij_idea`, 140 cols), Android Lint |

Notes on non-obvious version constraints:

* AGP 9 provides **built-in Kotlin**; the `org.jetbrains.kotlin.android` plugin must not
  be applied. AGP 9.3.2 bundles KGP 2.2.10, but KSP 2.3.x requires KGP 2.3.x, so the root
  `buildscript` classpath pins KGP 2.3.21 + KSP 2.3.11. Older KSP releases register
  generated sources through `kotlin.sourceSets`, which built-in Kotlin rejects.
* No alpha/beta/RC dependency is used anywhere. If one ever becomes unavoidable, document
  the capability that has no stable implementation here.

## 5. Alarm architecture

```text
Alarm (domain) → AlarmRepository → AlarmScheduler → AlarmManager.setAlarmClock()
        → AlarmReceiver → AlarmPlaybackService (mediaPlayback FGS)
                        → full-screen-intent notification → RingingActivity
                                                          → CameraX evidence
                                                          → Snooze / Dismiss
                                                          → AlarmOccurrence + EvidenceSegments
                                                          → History UI
```

Component responsibilities:

* `AlarmScheduler` — computes the next occurrence and programs exactly one exact alarm
  per upcoming occurrence. Idempotent. Reports failure explicitly.
* `AlarmReceiver` — Direct-Boot aware `BroadcastReceiver`; does no long work; starts
  `AlarmPlaybackService` and hands off; must not touch credential-protected Room before
  `UserManager.isUserUnlocked()` is verified.
* `AlarmPlaybackService` — foreground service (`mediaPlayback`) owning ringtone,
  vibration, wake lock, notification and ringing state.
* `RingingActivity` — `showWhenLocked` + `turnScreenOn`; owns user interaction and the
  CameraX evidence lifecycle.

## 6. Alarm scheduling rules

* Only `AlarmManager.setAlarmClock()` schedules real alarm occurrences. Forbidden:
  `Handler`, coroutine `delay`, WorkManager, periodic workers, `setRepeating()`, plain
  scheduled notifications, always-running services.
* Recurring alarms are recurrence **rules**; after firing, compute and schedule the next
  local occurrence. Never add fixed 24h deltas.
* Local-time semantics: "07:30 Mon–Fri" means 07:30 in the user's current zone on those
  weekdays, across DST gaps/overlaps and timezone changes.
* Re-evaluate all alarms on: `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`,
  `TIME_SET`, `TIMEZONE_CHANGED`, user unlock, exact-alarm permission state changes, and **every app
  launch** (`MainViewModel`) — the only supported recovery after a force stop, which the platform does not
  announce. Rescheduling is idempotent, so repeating it is safe.
* PendingIntent identity is deterministic and collision-safe (derived from the alarm id
  and occurrence kind), so rescheduling replaces rather than duplicates.
* Exact-alarm access: the app declares `USE_EXACT_ALARM` (it is a genuine alarm clock).
  Where exact access can still be unavailable, detect it, surface it on the reliability
  screen with a settings deep link, and reschedule once restored. **Never** silently
  downgrade to an inexact alarm.

## 7. Full-screen-intent rules

* Ringing is surfaced via a high-importance alarm-category notification whose
  `fullScreenIntent` launches `RingingActivity`. Background `startActivity()` is not the
  architecture.
* `RingingActivity` uses `setShowWhenLocked(true)` and `setTurnScreenOn(true)` and keeps
  the screen on while ringing. It never dismisses the keyguard: after dismissal the device
  stays locked.
* When Android shows a heads-up notification instead (device in active use), respect it.
  The notification routes to `RingingActivity`; it must not offer a Dismiss action that
  bypasses the recording experience. Snooze also happens in the Activity.
* Forbidden: overlay/`SYSTEM_ALERT_WINDOW`, accessibility services, device admin,
  keyguard bypass, hidden/undocumented window APIs.

## 8. AlarmPlaybackService responsibility

Owns and is the single source of truth for: ringtone playback (`AudioAttributes`
`USAGE_ALARM` / `CONTENT_TYPE_SONIFICATION`), vibration, the alarm notification lifetime,
the alarm wake lock, ringing state exposed to the UI, snooze/dismiss command handling, and
auto-timeout. It must keep ringing if `RingingActivity` crashes or disappears. It must not
depend on Compose, Room, or the camera. Do not request DND policy access.

## 9. RingingActivity responsibility

Owns interaction only: shows time/label, Snooze and Dismiss, recording status, optional
preview; drives the CameraX evidence lifecycle; sends commands to the service. It never
plays audio or vibrates. Dismiss/Snooze must work while camera state is broken, and must
remain large, high-contrast and reachable.

## 10. Camera / evidence lifecycle rules

* CameraX use cases bind to the `RingingActivity` lifecycle; recording starts only after
  the activity is actually resumed and stops when it is no longer visible.
* Front camera, `VideoCapture<Recorder>`, **no audio**, quality capped at 720p (fallback
  480p). `RECORD_AUDIO` is never requested or declared.
* No camera foreground service; no background recording; recording state is always visible
  in the UI (indicator + elapsed timer, not color-only).
* Timestamps recorded per occurrence: `scheduledFor`, `alarmTriggeredAt`, `activityVisibleAt`,
  and per segment `recordingRequestedAt`, `recordingStartedAt?`, `recordingEndedAt?`,
  `recordingFinalizedAt?`, plus `dismissedAt?` on the occurrence. `recordingStartedAt` comes
  from `VideoRecordEvent.Start`, never from activity creation.
* One occurrence may own multiple `EvidenceSegment`s (interruption → resume = new segment).
  Never concatenate video during capture; history plays segments sequentially.
* Every expected failure (permission denied/revoked, camera unavailable or in use, init
  failure, start failure, finalization failure, storage error, Direct Boot unavailable,
  unknown) is persisted as a machine-readable status and never blocks dismissal.

## 11. Direct Boot constraints

* Alarm-critical components (`AlarmReceiver`, `BootReceiver`, `AlarmPlaybackService`,
  `RingingActivity`) are `directBootAware`.
* A **minimal** alarm-critical mirror (id, trigger time, label, vibration, snooze minutes,
  sound URI) lives in device-protected storage; it holds nothing else about the user.
* Any code reachable before first unlock must check `UserManager.isUserUnlocked()` before
  touching Room/DataStore. Hilt dependencies used on that path must not eagerly build Room.
* Before first unlock, evidence is best-effort; when it cannot run, persist
  `DIRECT_BOOT_UNAVAILABLE` and still ring, snooze and dismiss normally.

## 12. Storage and privacy rules

* Local-first, offline: no account, backend, analytics, ads, crash SaaS, cloud sync, or
  any network permission. The app declares no `INTERNET` permission.
* Video files live under app-private `noBackupFilesDir/evidence/` with opaque generated
  names (no labels or timestamps in filenames). `allowBackup=false`, no cloud backup rules.
* Room stores metadata only — never video blobs, never localized display strings.
* Retention: default 7 days, options 1/7/30 days or indefinite. Cleanup is asynchronous and
  never touches segments belonging to an in-progress recording.
* Logs never contain video content, file paths of evidence, or user labels in release builds.

## 13. Compose architecture conventions

* Unidirectional data flow: repository/use case → `Flow` → `@HiltViewModel` →
  `StateFlow<UiState>` → composable → events back to the ViewModel.
* Collect with `collectAsStateWithLifecycle()`. UI state types are immutable.
* Composables receive state + callbacks; ViewModels are not passed into reusable
  composables; navigation stays at screen/root boundaries.
* No Room/DataStore access and no business logic inside composables. No `GlobalScope`,
  no ad-hoc application scopes without explicit lifecycle/cancellation semantics.
* Use `SavedStateHandle` for state that must survive process death.
* All user-facing text comes from string resources; dates/times are formatted locale-aware.

## 14. Hilt / DI conventions

* `@HiltAndroidApp` application; `@AndroidEntryPoint` on activities, services and receivers
  that need injection; `@HiltViewModel` for ViewModels; constructor injection by default.
* Modules only where constructor injection cannot work (framework types, Room, DataStore).
* Direct-Boot-safe dependencies must not transitively construct Room or DataStore.
* No service-locator patterns, no static mutable singletons.

## 15. Kotlin / code-quality conventions

Prefer immutable data, sealed interfaces for finite states, value classes where they add
type safety, explicit result types for expected failures, pure functions for
recurrence/scheduling math, `suspend` for one-shot work and `Flow` for streams.

Avoid `!!` (documented exceptions only), broad `catch (Exception)` that hides errors,
magic constants, stringly-typed state, giant managers/ViewModels/composables, public
mutable state, hidden side effects, fire-and-forget coroutines, reflection, and premature
abstraction. Comments explain *why* and platform constraints, not syntax.

### License

UpYet is GPL-3.0-or-later. Every dependency must be license-compatible with that (Apache-2.0, MIT,
BSD and similar permissive terms are; anything copyleft-incompatible or proprietary is not). Do not copy
substantial code from other projects without checking its license and recording the attribution.

## 16. Dependency policy

Before adding a dependency: check whether the platform or AndroidX already provides it,
prefer AndroidX/Google-supported libraries, check maintenance status, avoid duplicates and
trivial-helper libraries. All versions live in `gradle/libs.versions.toml`. Stable releases
only.

## 17. Testing requirements

* **Unit (JVM)**: recurrence calculation (one-time, daily, weekday sets, before/after today,
  week rollover, DST spring-forward gap, DST fall-back overlap, timezone change), snooze
  math, scheduling identity, alarm/occurrence/evidence state transitions, entity mappers,
  retention policy selection, reliability check evaluation.
* **Instrumented**: Room creation + migrations, DAOs/repositories, Compose screens for list,
  editor, history, ringing controls, permission-state UI, navigation.
* **Device/emulator**: the end-to-end matrix in `docs/TESTING.md`, including Doze, process
  kill, reboot with and without unlock, and camera failure modes.
* New behavior ships with tests. Never delete or `@Ignore` a failing test to go green.

## 17b. Housekeeping on launch

`MainViewModel` runs exactly two things once per launch: `AlarmRescheduler.rescheduleAll()` (force-stop
recovery) and `RetentionCleaner.clean()` (evidence retention). Neither may become a background worker, and
neither may block the first frame.

## 18. Required validation commands

```bash
./gradlew spotlessApply     # format
./gradlew spotlessCheck
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Instrumented tests when a device/emulator is available:
`./gradlew connectedDebugAndroidTest`.

Release builds go through `./scripts/build-android-release.sh`. Signing material lives in
`~/.config/upyet/android-signing/` (never in the repository) or in the `UPYET_ANDROID_KEYSTORE`,
`UPYET_ANDROID_KEY_ALIAS` and `UPYET_ANDROID_KEYSTORE_PASSWORD` environment variables. Never commit a
keystore, a password, or a signing config that embeds either.

### Known remaining lint warnings (deliberate)

`./gradlew lint` reports zero errors. Thirteen warnings remain and each is intentional:
`NewerVersionAvailable` / `GradleDependency` (the version catalog is pinned on purpose),
`OldTargetApi` (targetSdk 36 is the product requirement), `UnusedAttribute` for
`showWhenLocked`/`turnScreenOn` (the equivalent APIs are called at runtime for API 26), and
`ObsoleteSdkInt` for the `mipmap-anydpi-v26` adaptive icon. Do not add a lint baseline and do not silence
these; if a new warning appears, fix its cause.

### Versioning

`upyet.version` in `gradle.properties` is the single source of truth (SemVer). `versionCode` is derived
from it (`0.1.0` → `100`), APK filenames carry it, and the Settings screen shows it so a bug report can
name a build. Bumping a version means: edit that one property, move the `CHANGELOG.md` entries out of
`[Unreleased]` into the new version, commit, then tag `vX.Y.Z`. Never hand-edit `versionCode`.

## 19. Agent workflow

1. Read this file. 2. Read `docs/ARCHITECTURE.md` and `docs/TESTING.md` as relevant.
3. Inspect the existing implementation before modifying it. 4. Name the invariant involved.
5. Check current official Android documentation for platform-sensitive behavior.
6. Implement the smallest coherent change that preserves the architecture. 7. Add/update
tests. 8. Format. 9. Test. 10. Lint. 11. Build. 12. Report what changed and its limitations.

Claims must be labelled honestly: *compile-verified*, *unit-tested*, *emulator-tested*,
*physical-device-tested*, or *reasoned but unverified*. Never claim device verification that
did not happen. Keep planning artifacts (`docs/superpowers/**`) local; do not commit them.

## 20. Explicitly prohibited approaches

* WorkManager / `setRepeating()` / timers / coroutine delays / notifications as alarms.
* Alarm logic that depends on the Activity, Compose, Room, or the camera.
* `previousTrigger + 24h` recurrence.
* Notification actions that dismiss the alarm without the ringing experience.
* Background or invisible camera use; camera foreground service; `RECORD_AUDIO`.
* Overlay windows, accessibility services, device admin, keyguard bypass, hidden APIs,
  reflection into the framework, root or shell commands, undocumented restart hacks,
  attempts to defeat Force Stop.
* Network access, analytics, crash SaaS, ads, cloud backup of evidence.
* Requesting permissions the product does not need (microphone, location, contacts,
  storage/media, overlay, accessibility, device admin, background camera).
* Blanket battery-optimization exemption requests by default.
* Destructive Room migrations in release configuration.
* Blanket lint suppressions or disabling checks to make CI green.
