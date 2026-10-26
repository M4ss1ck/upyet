# AGENTS.md - UpYet

Authoritative engineering contract for every coding agent working in this repository.
Read this file before changing anything. If a request conflicts with the invariants
below, preserve the invariants and document the conflict instead of silently weakening
alarm reliability or platform security.

```text
ALARM RELIABILITY IS MORE IMPORTANT THAN EVIDENCE RECORDING.

The alarm must never depend on the camera, Compose UI, Room database,
or credential-protected storage being available.

Use AlarmManager.setAlarmClock() for alarm occurrences. Timers use
setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP); nothing else
schedules exact alarms.

Do not implement alarm scheduling with WorkManager, in-process timers
(`Handler`, `java.util.Timer`, coroutine `delay`), setRepeating(), or
ordinary notifications.

AlarmPlaybackService owns ringing.
RingingActivity owns interaction.
CameraX owns evidence while a ringing screen (RingingActivity or
TimerRingingActivity) is visible.

Never silently record from the background.

Never bypass Android keyguard security.

Never make inability to record video prevent alarm dismissal.

Never access credential-protected Room storage from a Direct Boot path
before verifying the user is unlocked.
```

## 1. Product definition

UpYet is a replacement for the phone's Clock app: Alarms, World clock, Timer and
Stopwatch. Its distinguishing feature is **video evidence of the user dismissing the
alarm**: while our own ringing screen is visible, the front camera records silent video,
and the resulting occurrence answers one question later: *"Did I actually wake up and
dismiss that alarm?"*

UpYet is **not** a companion for the system Clock app, a system-wide unlock recorder,
an intruder-selfie app, a background surveillance tool, or a generic reminder app: a timer
counts a duration, it reminds of nothing. The camera runs only during our own visible
ringing experience. The stopwatch and world clock never use the camera. A timer records
only when its evidence toggle is on; the toggle is off by default.

A **Clock tool** is the World clock, the Timer or the Stopwatch: any UpYet feature other
than Alarms.

## 2. Product invariants

1. A scheduled alarm rings at its wall-clock time even if the UI process was killed,
   the screen is off, the device is locked, the device is in Doze, or the device
   rebooted after scheduling.
2. Ringing (audio, vibration, notification, wake lock) is owned by a foreground
   service and never by an Activity.
3. The alarm is always dismissible. Any camera, storage, database, or UI failure is a
   recorded state, never a blocker and never a crash.
4. Recording happens only while a ringing screen (`RingingActivity` or
   `TimerRingingActivity`) is actually visible/resumed.
5. `recordingStartedAt` is set only when CameraX reports the recording actually started.
6. Recurrence is local-time based. Never `previous + 24h`.
7. Scheduling and cancellation are idempotent and use deterministic PendingIntent identity.
8. Evidence is local-only: app-private, non-backed-up storage; metadata in Room; no
   network of any kind.
9. Deleting an occurrence deletes its evidence files. Deleting an alarm does not
   silently delete past evidence.
10. If scheduling fails, the UI must not claim the alarm is active.
11. An occurrence has a source: alarm or timer. An occurrence is one recorded ringing of an
    alarm or a timer, the thing History records. Alarm occurrences keep everything: snooze
    chains, wake-ups, stats. A timer occurrence never snoozes and never forms a wake-up,
    and wake-up stats stay alarm-only. History shows both sources in one list, timer
    entries carry a "Timer" badge, and there is no extra filter. Several timers ringing
    together share one showing of the timer ringing screen: that showing produces exactly
    one timer occurrence and one set of evidence segments, and a timer that starts ringing
    while the screen is showing joins it (ADR 0010).
12. Timer evidence is two switches: a global "Record when a timer rings", off by default,
    and a per-timer toggle that defaults to the global value at creation. Both are editable
    while the timer is Idle, Running or Paused, never while it is Ringing. Changing the
    global switch never changes an existing timer; a timer created by a `SET_TIMER` intent
    takes the global value (ADR 0010).
13. With mixed toggles, recording runs while at least one recording-on timer is ringing and
    stops when the last of them is stopped or given +1 min, even if other timers still ring.
    A +1 min ends that timer's participation in the showing exactly like Stop. The occurrence,
    unlike the recording, spans the whole showing: it closes when the showing ends and never
    while a recording-on timer is still ringing, so a timer that rings again while the showing
    continues rejoins the same occurrence, and only a ring after the showing ended is a new
    showing and a new occurrence. The occurrence lists only the recording-on timers. A showing
    in which every ringing timer has recording off produces no occurrence and nothing in
    History, unless a recording-on timer joins it while it is still up (ADR 0010).
14. Timer evidence obeys the alarm evidence rules unchanged: deleting a timer occurrence
    deletes its files, deleting a timer keeps its past evidence, a missed timer records
    nothing because it never rings, and a recording-on ring before first unlock persists
    `DIRECT_BOOT_UNAVAILABLE`. Stop and +1 min always work regardless of camera state.

## 3. Supported platform

* minSdk 26, targetSdk 36, compileSdk 37 (compileSdk is ahead of targetSdk only because
  current stable AndroidX artifacts require it; runtime behavior remains targetSdk 36).
* Single Gradle application module `:app`.
* JDK 17 language level; Gradle runs on JDK 17+.

## 4. Stack (verified stable on 2026-08-24)

| Concern | Choice |
|---|---|
| Build | AGP 9.3.3 (built-in Kotlin), Gradle 9.7.1, Kotlin 2.3.21, KSP 2.3.12 |
| UI | Jetpack Compose (BOM 2026.08.00), Material 3, Navigation Compose |
| DI | Hilt 2.60.1 |
| Persistence | Room 2.8.5 (metadata), DataStore Preferences 1.2.1 (settings) |
| Camera | CameraX 1.6.2 (`camera-video`, `camera-compose`) |
| Playback of evidence | Media3 1.10.1 (`media3-exoplayer`, `media3-ui`, `media3-ui-compose`) |
| Time | `java.time` only |
| Format/lint | Spotless + ktlint 1.8.0 (Kotlin official style = ktlint `intellij_idea`, 140 cols), Android Lint |

Notes on non-obvious version constraints:

* AGP 9 provides **built-in Kotlin**; the `org.jetbrains.kotlin.android` plugin must not
  be applied. AGP 9.3.3 bundles KGP 2.2.10, but KSP 2.3.x requires KGP 2.3.x, so the root
  `buildscript` classpath pins KGP 2.3.21 + KSP 2.3.12. Older KSP releases register
  generated sources through `kotlin.sourceSets`, which built-in Kotlin rejects.
* No alpha/beta/RC dependency is used anywhere. If one ever becomes unavoidable, document
  the capability that has no stable implementation here.
* `media3-ui-compose` is a stable release, but every symbol in it carries Media3's
  `@UnstableApi` opt-in marker. The evidence player opts in explicitly with
  `@OptIn(UnstableApi::class)` on the two composables that use it. That opt-in is Media3's
  own mechanism, not a lint suppression - do not widen it to a file or module level.

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

* `AlarmScheduler`: computes the next occurrence and programs exactly one exact alarm
  per upcoming occurrence. Idempotent. Reports failure explicitly.
* `AlarmReceiver`: Direct-Boot aware `BroadcastReceiver`; does no long work; starts
  `AlarmPlaybackService` and hands off; must not touch credential-protected Room before
  `UserManager.isUserUnlocked()` is verified.
* `AlarmPlaybackService`: foreground service (`mediaPlayback`) owning ringtone,
  vibration, wake lock, notification and ringing state.
* `RingingActivity`: `showWhenLocked` + `turnScreenOn`; owns user interaction and the
  CameraX evidence lifecycle.

## 5a. Timer architecture

Timers borrow the alarm's reliability path, never its stateful objects: the only classes the
two paths share are `AlarmSoundPlayer` and `AlarmVibrator`. The state machine, the
ringing surface and the persistence split are ADR 0008; the scheduling carve-out is
ADR 0009; the evidence rules are ADR 0010.

```text
Timer (domain) → TimerRepository → TimerScheduler
        → AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)
                                (one alarm, for the earliest-ending timer)
        → TimerReceiver → TimerPlaybackService (mediaPlayback FGS)
                        → full-screen-intent notification → TimerRingingActivity
                                                          → shared evidence component
                                                          → Stop / +1 min / Stop all
                                                          → TimerOccurrence + EvidenceSegments
                                                          → History UI
```

Component responsibilities:

* `TimerRepository`: Room is the truth for the list, the labels and editing; the
  device-protected mirror is authoritative before first unlock and is reconciled into
  Room on `USER_UNLOCKED`.
* `TimerScheduler`: keeps exactly one exact alarm, for the earliest-ending timer (§6a).
  Idempotent. Reports failure explicitly.
* `TimerReceiver`: Direct-Boot aware `BroadcastReceiver`; does no long work; starts
  `TimerPlaybackService` and hands off; must not touch credential-protected Room before
  `UserManager.isUserUnlocked()` is verified.
* `TimerPlaybackService`: foreground service (`mediaPlayback`) owning the timer ringtone,
  vibration, one wake lock, the notification and ringing state for every ringing timer at
  once (§8a).
* `TimerRingingActivity`: `showWhenLocked` + `turnScreenOn`; owns user interaction and the
  evidence lifecycle (§9a).
* Timer run state is exactly one of `Idle`, `Running`, `Paused`, `Ringing`, `Missed`.
  Transitions are the table in ADR 0008 and are pure functions of the stored anchors.

No always-running service. A `Running` timer and a running stopwatch are plain ongoing
notifications computed from stored anchors: no foreground service and no wake lock. Only a
ringing timer (`TimerPlaybackService`) or a ringing alarm (`AlarmPlaybackService`) holds an
FGS.

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
  launch** (`MainViewModel`), the only supported recovery after a force stop, which the platform does not
  announce. Rescheduling is idempotent, so repeating it is safe.
* PendingIntent identity is deterministic and collision-safe (derived from the alarm id
  and occurrence kind), so rescheduling replaces rather than duplicates.
* Exact-alarm access: the app declares `USE_EXACT_ALARM` (it is a genuine alarm clock).
  Where exact access can still be unavailable, detect it, surface it on the reliability
  screen with a settings deep link, and reschedule once restored. **Never** silently
  downgrade to an inexact alarm.

## 6a. Timer scheduling rules

* The one exception to §6, and a closed one (ADR 0009): a timer schedules exactly one
  exact alarm with `AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)`, one
  fixed request code, for the earliest-ending timer (ADR 0008). Only timers, and only that
  API. An alarm occurrence is never scheduled with it, and a timer is never scheduled with
  `setAlarmClock()`: a countdown is not the device's next alarm and must not be reported as
  one on the status bar, lock screen or keyguard.
* A `Running` timer stores `remainingAtAnchor` alongside both an elapsed anchor and a
  wall-clock anchor, because `elapsedRealtime` resets at boot. Remaining time is a pure
  function of either pair, clamped at zero.
* Re-arm after every timer mutation, on boot from the device-protected mirror, on user
  unlock and on every app launch. Re-arming is idempotent.
* A delivery is validated against the timer's current end instant and dropped as stale when
  it no longer matches, so a cancelled, paused, reset or extended timer cannot ring an
  alarm that is no longer its own.
* Elapsed-realtime alarms do not move when the wall clock changes: `TIME_SET` and
  `TIMEZONE_CHANGED` re-arm nothing, they recompute remaining time from the elapsed delta
  and ignore a negative delta.
* Discovery (`LOCKED_BOOT_COMPLETED`, `USER_UNLOCKED`, app launch) moves a `Running` timer
  that is more than 60 s overdue to `Missed`: it does not ring, records nothing and waits
  under a low-importance notification. A timer 0 to 60 s overdue rings through an immediate
  exact alarm, so the platform still authorises the foreground service start.
* `USE_EXACT_ALARM` covers timers as well as alarms. Where exact access is unavailable the
  §6 rule applies unchanged: detect it, surface it, never downgrade to an inexact alarm.

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

## 8a. TimerPlaybackService responsibility

Mirrors §8 for timers and shares only `AlarmSoundPlayer` and `AlarmVibrator` with the alarm
path (ADR 0008); reusing `AlarmPlaybackService` is forbidden, because that object owns
occurrences, snooze budgets, evidence and the single alarm ringing session. Owns and is the
single source of truth for: the timer ringtone, vibration, one wake lock and one sound
serving every ringing timer at once, the timer ringing notification (whose text is the
count of ringing timers), ringing state exposed to the UI, and stop / +1 min command
handling. It must keep ringing if `TimerRingingActivity` crashes or disappears. It must not
depend on Compose, Room, the camera or the evidence component.

## 9. RingingActivity responsibility

Owns interaction only: shows time/label, Snooze and Dismiss, recording status, optional
preview; drives the CameraX evidence lifecycle; sends commands to the service. It never
plays audio or vibrates. Dismiss/Snooze must work while camera state is broken, and must
remain large, high-contrast and reachable.

## 9a. TimerRingingActivity responsibility

Owns interaction only, for the timers that are ringing: lists every ringing timer with
Stop and +1 min, adds Stop all when two or more are ringing, shows recording status, drives
the evidence lifecycle through the same component `RingingActivity` uses (§10, never two
copies), and sends commands to `TimerPlaybackService`. It never plays audio or vibrates.
Pause is not offered while a timer is ringing. Stop, Stop all and +1 min must work while
camera state is broken, and must remain large, high-contrast and reachable. The §7 rules
apply to it unchanged: a high-importance alarm-category notification with a
`fullScreenIntent`, `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, and never a
keyguard bypass.

## 10. Camera / evidence lifecycle rules

* CameraX use cases bind to the ringing screen's lifecycle (`RingingActivity` or
  `TimerRingingActivity`); recording starts only after the activity is actually resumed and
  stops when it is no longer visible. Both screens use one evidence component, never two
  copies (ADR 0010).
* Front camera, `VideoCapture<Recorder>`, **no audio**, quality capped at 720p (fallback
  480p). `RECORD_AUDIO` is never requested or declared.
* No camera foreground service; no background recording; recording state is always visible
  in the UI (indicator + elapsed timer, not color-only).
* One camera permission serves both ringing screens. Turning timer evidence on uses it, or
  requests it at that moment; there is no timer-specific permission and no new permission at
  all. The prominent disclosure (ADR 0006) must say that timers can record too, when the user
  turns that on, and the card must gate the timer toggle as well: turning Timer evidence on,
  in the Settings Timers section or per timer, while the camera permission is not granted,
  shows that card first, so the system camera dialog is never the first thing the user sees.
* A `DISMISS_TIMER` intent never ends a recording ring. When it matches a ringing timer whose
  Timer evidence is on, `TimerRingingActivity` is brought forward (the device stays locked)
  and the timer keeps ringing, exactly as `DISMISS_ALARM` brings `RingingActivity` forward
  for a ringing alarm: through the existing ringing notification's full-screen intent, or its
  content intent when the platform shows the heads-up instead, never a background
  `startActivity()` (ADR 0007, amendment of 2026-10-24). A ringing timer with Timer evidence
  off stops directly; a non-ringing timer is unchanged.
* Timestamps recorded per occurrence: `scheduledFor`, `triggeredAt`, `activityVisibleAt`,
  and per segment `recordingRequestedAt`, `recordingStartedAt?`, `recordingEndedAt?`,
  `recordingFinalizedAt?`, plus `dismissedAt?` on the occurrence. `triggeredAt` is the code's
  neutral name for the moment the ring started, alarm or timer, and `recordingStartedAt`
  comes from `VideoRecordEvent.Start`, never from activity creation.
* One occurrence may own multiple `EvidenceSegment`s (interruption → resume = new segment).
  Never concatenate video during capture; history plays segments sequentially.
* Every expected failure (permission denied/revoked, camera unavailable or in use, init
  failure, start failure, finalization failure, storage error, Direct Boot unavailable,
  unknown) is persisted as a machine-readable status and never blocks dismissal.
* The same rules govern a timer occurrence: the timer ringing screen drives the same
  component and records the same per-occurrence and per-segment timestamps, and
  `recordingStartedAt` still comes only from `VideoRecordEvent.Start`. A timer occurrence
  ends at a stop or a +1 min rather than a dismissal, so it ends with the same user-ended
  field an alarm dismissal uses, `dismissedAt?`, recorded when the showing ends and meaning
  "the user ended this ring". Recording still stops with the last recording-on timer, but the
  occurrence stays open while any timer rings: a +1 min takes that timer out of the showing
  exactly like Stop, a timer that rings again while the showing continues rejoins that same
  occurrence, and only after the showing has ended is a ring a new showing and a new
  occurrence. A missed timer never records because it never rings.
* A timer occurrence carries `source = TIMER` plus a snapshot of the recording-on timers it
  covered: their id and their label, stored as text, never a foreign key, because deleting a
  timer keeps its past evidence. The source and that snapshot land in the same
  non-destructive migration (ADR 0010).

## 11. Direct Boot constraints

* Alarm-critical and timer-critical components (`AlarmReceiver`, `BootReceiver`,
  `AlarmPlaybackService`, `RingingActivity`, `TimerReceiver`, `TimerPlaybackService`,
  `TimerRingingActivity`) are `directBootAware`.
* A **minimal** alarm-critical mirror (id, trigger time, label, vibration, snooze minutes,
  sound URI) lives in device-protected storage; it holds nothing else about the user.
* A separate **minimal** timer mirror (id, state, total length, remaining at its anchor,
  wall-clock anchor, end instant, label, ringtone URI, and the per-timer Timer evidence
  flag) lives in device-protected storage (ADR 0008, ADR 0010). It carries the timer label,
  because before first unlock the ringing screen has no other way to tell one ringing timer
  from another, and it carries the evidence flag, because the pre-unlock path cannot ask
  Room which timers are recording-on. Alarm labels are not mirrored, and that does not
  change.
* A recording-on timer that rings before first unlock writes its pending occurrence, with
  its timer attribution and the `DIRECT_BOOT_UNAVAILABLE` status, through the same
  device-protected `PendingOccurrenceStore` the alarm path uses
  (`app/src/main/java/dev/upyet/core/directboot/`). That record carries an explicit alarm /
  timer source discriminator, and `USER_UNLOCKED` reconciles it into Room the same way it
  reconciles the timer mirror, dispatching on that source: a timer record is never applied as
  an alarm record and never triggers an alarm-only effect such as retiring a one-time alarm
  with the timer's id (ADR 0010). The encoding stays backward compatible: a record written
  before the discriminator existed decodes as an alarm record, so an upgrade never drops an
  in-flight alarm. A timer occurrence ended from the ringing screen has its own `STOPPED`
  outcome, never an alarm outcome.
* Any code reachable before first unlock must check `UserManager.isUserUnlocked()` before
  touching Room/DataStore. Hilt dependencies used on that path must not eagerly build Room.
* Before first unlock, evidence is best-effort; when it cannot run, persist
  `DIRECT_BOOT_UNAVAILABLE` and still ring, snooze, dismiss, stop and +1 min normally.

## 12. Storage and privacy rules

* Local-first, offline: no account, backend, analytics, ads, crash SaaS, cloud sync, or
  any network permission. The app declares no `INTERNET` permission.
* Video files live under app-private `noBackupFilesDir/evidence/` with opaque generated
  names (no labels or timestamps in filenames). `allowBackup=false`, no cloud backup rules.
* Room stores metadata only: never video blobs, never localized display strings.
* Retention: default 7 days, options 1/7/30 days or indefinite. Cleanup is asynchronous and
  never touches segments belonging to an in-progress recording.
* Retention and deletion are identical for timer occurrences (ADR 0010): deleting a timer
  occurrence deletes its files, deleting a timer keeps its past evidence, and a missed timer
  never records because it never rings.
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
  retention policy selection, reliability check evaluation, the timer run-state machine and
  its transition table (ADR 0008), remaining-time math from both anchors including a
  negative elapsed delta, timer scheduling identity and stale-delivery rejection, timer
  intent handling (`SET_TIMER`, `DISMISS_TIMER`; ADR 0007), occurrence source and timer
  evidence toggle resolution, and pending-occurrence reconciliation dispatching on the
  record's source.
* **Instrumented**: Room creation + migrations (including the occurrence-source and
  timer-attribution migration), DAOs/repositories, Compose screens for list, editor, history,
  ringing controls, permission-state UI, navigation, the disclosure card gating the timer
  evidence toggle, the timer screens (list, editor, ringing), the Settings Timers section,
  and timer history entries with the Timer badge.
* **Device/emulator**: the end-to-end matrix in `docs/TESTING.md`, including Doze, process
  kill, reboot with and without unlock, and camera failure modes.
* New behavior ships with tests. Never delete or `@Ignore` a failing test to go green.

## 17b. Housekeeping on launch

`MainViewModel` runs three steps once per launch, in this order:

1. `AlarmRescheduler.rescheduleAll()`: the only supported force-stop recovery, since the platform does not
   announce a force stop. It is idempotent, reports its own counts and failures, and also recomputes the
   upcoming-alarm notification, which a force stop clears. Its other callers are `BootReceiver`,
   `UserUnlockedReceiver` and `AlarmPlaybackService`.
2. `TimerRearm.rearm()`: the timer re-arm (§6a, one exact alarm for the earliest-ending timer), for the same
   reason. Until the timer scheduler lands (slice #35) the bound implementation is `NoOpTimerRearm`; its
   replacement must stay idempotent.
3. `RetentionCleaner.clean()` (evidence retention), last, because alarm reliability outranks evidence.

None of them may become a background worker, and none may block the first frame: the whole sequence runs on
the injected `@DefaultDispatcher`, so constructing the ViewModel does no work on the main thread. Each step
is isolated, so a throw in one is logged as `launch_housekeeping_failed` and the others still run.
`MainViewModelTest` pins the order, the once-per-launch count, the isolation and the dispatcher.

## 18. Required validation commands

```bash
./gradlew spotlessApply     # format
./gradlew spotlessCheck compileDebugAndroidTestKotlin test lint assembleDebug
```

The second line is exactly what CI runs (`.github/workflows/ci.yml`) on every pull request and push to
`main`. `compileDebugAndroidTestKotlin` is there because nothing else compiles the instrumented tests
without a device.

Instrumented tests when a device/emulator is available:
`./gradlew connectedDebugAndroidTest`.

Release builds go through `./scripts/build-android-release.sh`. Signing material lives in
`~/.config/upyet/android-signing/` (never in the repository) or in the `UPYET_ANDROID_KEYSTORE`,
`UPYET_ANDROID_KEY_ALIAS` and `UPYET_ANDROID_KEYSTORE_PASSWORD` environment variables. Never commit a
keystore, a password, or a signing config that embeds either.

### Zero-warning policy

The build has no warnings, and the build config keeps it that way: Kotlin compiles with
`allWarningsAsErrors` and lint runs with `warningsAsErrors`, so a new warning fails the build locally and
in CI. Fix the cause. Do not add a lint baseline, a blanket `@Suppress`, a file- or module-level opt-in,
or an entry in `app/lint.xml` to get green. A narrow `@Suppress("DEPRECATION")` is acceptable only on a
call behind an API-level check whose replacement does not exist on the older releases, with a comment
saying so (`AlarmVibrator`, `RingingActivity`).

`app/lint.xml` makes four checks informational: `NewerVersionAvailable`, `GradleDependency` and
`AndroidGradlePluginVersion` (they report what was published upstream, so as gates they would break a
green commit overnight) and `OldTargetApi` (targetSdk 36 is the product requirement). They still appear in
every lint report. Dependency policy: take patch releases when they appear; take minor and major releases
only for a needed feature or a security fix. The `kotlinx-serialization` entries move when Room's
`MigrationTestHelper` needs them to, not when serialization releases.

### Versioning

`upyet.version` in `gradle.properties` is the single source of truth (SemVer). `versionCode` is derived
from it (`0.1.0` → `100`), APK filenames carry it, and the Settings screen shows it so a bug report can
name a build. Bumping a version means: edit that one property, move the `CHANGELOG.md` entries out of
`[Unreleased]` into the new version, commit, then tag `vX.Y.Z`. Never hand-edit `versionCode`.

The full release procedure - validation, tagging, the signed APK, the GitHub release and the Play
submission - is `docs/RELEASING.md`; everything Play asks for that is not the build is `docs/STORE.md`.

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

* WorkManager / `setRepeating()` / in-process timers (`Handler`, `java.util.Timer`, coroutine
  `delay`) / notifications as alarms.
* Alarm logic that depends on the Activity, Compose, Room, or the camera.
* `previousTrigger + 24h` recurrence.
* Notification actions that dismiss the alarm without the ringing experience.
* Any exact alarm other than `setAlarmClock()` for an alarm and
  `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)` for the earliest-ending timer. The
  timer carve-out is closed: it admits no other API and no other caller.
* Timer logic that depends on the Activity, Compose, Room, or the camera, or that reuses
  `AlarmPlaybackService` or `RingingActivity` for timers.
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
