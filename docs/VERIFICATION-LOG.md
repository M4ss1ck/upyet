# UpYet — verification log

The record of what was actually run against real hardware, and what happened.

**Append-only.** Newest run first. An entry is never edited after the fact — not to correct a result, not
to tidy a note. A later run supersedes an earlier one by being newer, and the earlier entry stays exactly
as it was written. The only permitted edits to a past entry are typographical.

Read it with `docs/TESTING.md` open: the scenario slugs used here are defined in that file's matrix, and
the lanes (`automated` / `adb` / `human-eyes`) mean what §4 says they mean.

Rules for an entry:

* Name the hardware and the package. An emulator run is labelled emulator; a `physical` scenario run on
  an emulator is a regression result, never a device result.
* Every scenario attempted gets a row: `PASS`, `FAIL` or `SKIPPED`. A scenario that was not attempted is
  simply absent — but a scenario that was skipped on purpose is present, with the reason.
* Findings are written down whether or not they were fixed. A `FAIL` that turned out to be the test's
  fault is still a finding.

## Entry template

```markdown
## <ISO date> — <device model>, Android <ver> (API <n>), <debug|release> <upyet.version>

Lane: <automated | adb | human-eyes | mixed>   Build: <versionName> (<versionCode>)   Package: <applicationId>

| Scenario | Result | Notes |
|---|---|---|
| core-path | PASS | … |

### Findings
- …
```

## Runs

## 2026-10-27 — emulator (`omnoku_pixel` AVD), Android 15 (API 35), debug 0.3.0

Lane: mixed (automated + adb)   Build: 0.3.0 (300)   Package: `dev.upyet.debug`

The 0.3.0 release gate (#48): `core-path`, `timer-core-path` and the every-release smoke subset. Emulator run,
so a regression result only; the AVD was started with `-camera-front emulated` and has no lock PIN, so no row
here says anything about the keyguard. Automated: `connectedDebugAndroidTest`, 182 tests, all pass. The adb
scenarios used the debug `SCHEDULE` / `TIMER` receivers with evidence on, read `logcat -s UpYet`, `dumpsys` and
the Room tables, and tapped through `uiautomator`. The signed release APK (`dev.upyet`, 0.3.0 / 300) was built
with `scripts/build-android-release.sh` but not installed.

| Scenario | Result | Notes |
|---|---|---|
| core-path | PASS | `CorePathFlowTest` (automated lane). Regression result: no keyguard on this AVD |
| timer-core-path | FAIL, then PASS after the fix | 4 of 4 timer rings with the preview hidden (the default) finalized `FAILED`/`UNKNOWN`, 0 bytes: see findings. With the fix, 2 of 2 `RECORDED` (1.40 MB, 1.47 MB), occurrence `source=TIMER`, outcome `STOPPED`; a third ring toggling the preview on and off mid-recording kept one segment, 665 frames over 22.2 s |
| doze | PASS | stepped to deep `IDLE` with the battery unplugged and the screen off; `alarm_triggered` on time, clip `RECORDED`. A first attempt with `force-idle` alone stopped at `INACTIVE` and is not counted |
| process-killed | PASS | `kill -9` on the app pid (as root; `am kill` does not kill a non-cached process), new pid rang, clip `RECORDED` |
| camera-denied | PASS | `CAMERA` revoked: rang, dismissed, segment `SKIPPED` / `PERMISSION_DENIED` |
| snooze | PASS | Snooze 9 minutes: `alarm_snoozed`, SNOOZE alarm fired at 17:21:37.587 for 17:21:37.582, child occurrence linked to its parent, both clips `RECORDED`. The screen was awake (this AVD never times out), so the snooze ring came as a heads-up and the ringing screen was opened from it |
| reboot-unlocked | PASS | `adb reboot`, `alarms_rescheduled scheduled=1` after boot, rang, recorded, dismissed |
| core-path (physical) | SKIPPED | No physical device attached in this session. It is `physical` / `human-eyes`: the ringing screen above a real keyguard and a playable clip in History needs a person and a phone. Owed on a physical device before the `v0.3.0` tag |
| timer-core-path (physical) | SKIPPED | No physical device attached in this session. It is `physical` / `human-eyes`: the timer ringing screen above a real keyguard, the "Timer" badge and a playable clip in History needs a person and a phone. Owed on a physical device before the `v0.3.0` tag |

### Findings
- Every timer recording with the preview hidden produced no video. `TimerRingingActivity` binds Preview and
  VideoCapture together but composed `EvidencePreview` only while the preview toggle was on, which it is not by
  default. CameraX waits for every bound use case's surface before it starts the capture session and gives up
  after five seconds (`CXCP: Failed to get Surfaces: isActive=true, surfaces=[]`), so the encoder never saw a
  frame and the Recorder finalized with `ERROR_NO_VALID_DATA`, persisted as `FAILED`/`UNKNOWN`. Showing the
  preview inside those five seconds recorded normally; showing it later did not. Fixed: the timer screen keeps
  `EvidencePreview` composed while any timer records and shrinks it to 1 dp when hidden, so the surface exists
  and toggling resizes it instead of tearing it down. Pinned by
  `TimerRingingScreenTest.theSlotIsComposedHiddenWhileRecordingWithThePreviewOff`, which fails on the old
  condition. The alarm screen always shows its preview and never had the problem. The hidden 1 dp surface is a
  speck a few pixels wide on screen.
- No earlier entry records a timer clip that recorded: the timer slices were verified by instrumented screen
  tests and recording-state logs, neither of which looks at the file. `timer-core-path` joining the smoke subset
  is what caught it.
- `process-killed` and `reboot-unlocked` in `TESTING.md` pointed to "the lane note below", which did not
  exist, and the `am kill` recipe silently did nothing with the app in the foreground. Both lane notes are now
  written under the matrix.

## 2026-10-27 — emulator (`omnoku_pixel` AVD), Android 15 (API 35), debug 0.2.0

Lane: adb   Build: 0.2.0 (200)   Package: `dev.upyet.debug`

Branch `feat-alarmclock-intents` (#46). Emulator run, so a regression result only. Google Clock is also
installed, so every command named the component (`-n dev.upyet.debug/dev.upyet.intents.ClockIntentActivity`);
shell passed the `SET_ALARM` guard. A `SKIP_UI` toast ("Timer started") was seen on screen. Instrumented on the
same AVD: `MigrationSmokeTest` 9 (including 5 -> 6), `UpYetDatabaseTest` 8, `TabsScreenTest` 14, all pass.

| Scenario | Result | Notes |
|---|---|---|
| set-timer-intent | PASS | `SKIP_UI` timer armed, launcher stayed on top; `timers.evidenceEnabled` 0 with the global switch off, 1 with it on |
| timer-dismiss-intent | PASS | recording-off ringing timer: `timer_stopped`, notification 1101 gone. Recording-on, screen sent home: `TimerRingingActivity` resumed, `camera_bind_requested`, 1101 still posted. With `-d content://dev.upyet/timers/1` a Running timer went RUNNING -> IDLE |
| voice-alarm | PASS | stored `voice=1`; dismissed from the ringing screen: `voice_alarm_deleted`, `alarms` empty, occurrence kept as DISMISSED. Prefilled editor (07:15, "Gym", Mon+Wed) stored nothing. Burst of three: see findings |
| alarm-dismiss-intent | PASS | ringing (modes `next` and `label`): `RingingActivity` resumed, 1001 still posted. `all` over nine alarms: nothing changed, MainActivity opened. `time` 10 PM on a one-time alarm: `enabled=0`, row kept. Snoozed (`label`): nothing changed, snooze not cancelled. `SNOOZE_ALARM` resolves to nothing of ours |

### Findings
- A burst of three `SET_ALARM` commands created one alarm: the platform delivered the second and third
  ("delivered to currently running top-most instance") to the first trampoline after it had finished, and
  they were lost. Fixed with `documentLaunchMode="always"` on `ClockIntentActivity`; the same burst then
  created three alarms. Pinned by `ClockIntentManifestTest`.
- Bringing a ringing screen forward over the keyguard was not attempted: it needs the ringing screen in the
  background while locked. Physical-device check.

## 2026-10-07 — emulator (`omnoku_pixel` AVD), Android 15 (API 35), debug 0.1.7

Lane: automated   Build: 0.1.7 (107)   Package: `dev.upyet.debug`

Branch `chore-zero-warnings`: zero-warning build, dependency patch bumps (CameraX 1.6.2, Room 2.8.5,
Activity 1.12.4, AGP 9.3.3), Compose test rule v2, `VibrationAttributes` on API 33+. Emulator run, so a
regression result only. Started with `-camera-front emulated`.

| Scenario | Result | Notes |
|---|---|---|
| instrumented suite | PASS | 65/65 `connectedDebugAndroidTest`, every Compose UI test on the v2 `createComposeRule` (`StandardTestDispatcher`) |
| core-path | PASS | `CorePathFlowTest`: alarm woke the screen, `RingingActivity` focused, CameraX 1.6.2 reported Start, segment finalized, dismiss worked. Keyguard claim not exercised (no secure lock screen on this AVD) |
| vibration usage | PASS | `dumpsys vibrator_manager` for the core-path ring: `usage: ALARM`, waveform 0/700/500 ms repeating, ended `cancelled_by_user` at dismiss. The API 33+ `VibrationAttributes` path |

### Findings
- **First run failed `core-path` for want of a front camera, not because of the change.** The AVD defaults
  to `hw.camera.front=none`; CameraX logged `CameraIdListIncorrectException` and the recording indicator
  never appeared. Rerun with `-camera-front emulated`: green. The prerequisite is the one the 2026-08-26
  entry already records.

## 2026-08-27 (later) — emulator (`maibuk_test` AVD), Android 14 (API 34), debug 0.1.6

Lane: automated   Build: 0.1.6 (106)   Package: `dev.upyet.debug`

Regression-only run: the instrumented suite, after repairing it. AVD started with
`-camera-front emulated`. Not a physical-device result.

| Scenario | Result | Notes |
|---|---|---|
| instrumented-regression | PASS | 62 tests, 0 failures, 0 skipped. First green run of the whole suite since the stats card landed. |

### Findings

- **The suite had been dead, not passing.** `HistoryScreenTest` passed no value for `onStatsWindowChange`,
  so the entire `androidTest` source set failed to compile and `connectedDebugAndroidTest` did nothing at
  all. A compile failure in a test source set is silent in a way a failing test is not — nothing reports a
  red test, the task simply refuses to build.
- **A second failure was hiding behind the first.** With the compile fixed,
  `theLastFilterChipKeepsItsLabelOnOneLine` failed: "Missed" is both a history filter and a stats figure,
  so the assertion matched two nodes. The filter row now carries a test tag and the query is scoped to it.
  Both fixed in `b930638`.

## 2026-08-27 — emulator (`maibuk_test` AVD), Android 14 (API 34), debug 0.1.6

Lane: adb   Build: 0.1.6 (106)   Package: `dev.upyet.debug`

Emulator run covering the diagnostic report and the camera prominent disclosure only. Not a
physical-device result. The instrumented suite could not be used as the regression gate this time — see
the findings.

| Scenario | Result | Notes |
|---|---|---|
| diagnostic-report | PASS | Settings → About → Share a diagnostic report opens the share sheet with `upyet-diagnostics-2026-08-27-1547.txt` attached. File read back off the device with `run-as`: header, all eight reliability checks, seven settings and the event log all present and correctly formatted. |
| diagnostic-log-persistence | PASS | `/data/user_de/0/.../files/diagnostics/diagnostics.log` is written on the alarm path and survives an app reinstall, confirming device-protected storage. |
| scheduling-events | PASS | `alarm_scheduled` (with trigger time), `alarm_cancelled` and `alarms_rescheduled` all recorded on a `MY_PACKAGE_REPLACED` reschedule. |
| camera-disclosure | PASS | With CAMERA revoked, the card appears above the fold on the alarm list, headed "Before the camera turns on", and "Agree & continue" launches the system permission dialog. Granting removes the card. |
| instrumented-regression | SKIPPED | `compileDebugAndroidTestKotlin` does not compile on `main`; see findings. Superseded by the 2026-08-27 (later) entry, which ran it. |

### Findings

- **One reschedule was logged as two events.** The first diagnostic report read off the device showed
  `alarms_rescheduled` twice, one millisecond apart, with two spellings of the same fields — `BootReceiver`
  was already logging that event name, and `AlarmRescheduler` had just started logging its own. A log that
  reads as if the work happened twice. Fixed in `3749feb`: the receiver now logs only what triggered it.
  Reading the code did not find this; reading the report the code produced did.
- **`app/src/androidTest` does not compile on `main`, and predates this work.**
  `HistoryScreenTest.kt:158` passes no value for `onStatsWindowChange`, so `connectedDebugAndroidTest`
  cannot run at all. Verified identical at `a84ee78`, the commit before this work began — it arrived with
  the stats feature. Not fixed here: it is outside the diagnostic-report scope, and it means the whole
  instrumented suite has been unavailable as a release gate since then.

## 2026-08-26 — emulator (`maibuk_test` AVD), Android 14 (API 34), debug 0.1.5

Lane: mixed (automated + adb)   Build: 0.1.5 (105)   Package: `dev.upyet.debug`

Emulator run covering the upcoming-alarm notification (TODO item 7) only. Not a physical-device result.
AVD started with `-camera-front emulated`. The whole instrumented suite was run as a regression gate, then
the new feature was driven by hand through `adb` and the notification shade.

| Scenario | Result | Notes |
|---|---|---|
| instrumented suite | PASS | 52/52, 0 failures. The three failures recorded in the previous emulator entry (`AlarmListScreenTest.rendersAlarmTimeAndLabel`, `MigrationSmokeTest.opensVersionOne`, `NavigationTest`) did not reproduce on this AVD |
| upcoming notification posts | PASS | Alarm 30 min out, default 1 h lead. Posted at launch as id 1004 on `alarm_upcoming`, `importance=2`, `sound=null`, `vibrate=null`, `category=reminder`, one action. Title was the alarm's local time (`8:33 PM`), text the label (`Debug alarm`) |
| body tap | PASS | Opened the alarm list and auto-cancelled the notification |
| `Turn off` action | PASS | Correct label for a one-time alarm. Tapped in the real shade: notification cleared and both `MAIN` and `SNOOZE` exact alarms were cancelled |
| ringing clears it | PASS | Alarm scheduled 75 s out with the notification already posted; on `alarm_triggered` the upcoming notification was gone and only the ringing notification remained |
| `Skip` action (recurring) | NOT RUN | The debug receiver only creates one-time alarms, so only the `Turn off` branch was exercised on device. The `Skip` branch is covered by `SkipNextOccurrenceTest` |

### Findings

- **`rescheduleAll()` is never called at app launch,** contrary to `AGENTS.md` §6 and §17b, which both name
  `MainViewModel` as the force-stop recovery point. Its only live callers are `BootReceiver`,
  `UserUnlockedReceiver` and `AlarmPlaybackService`. Found because the upcoming notification never appeared:
  the launch recompute it was designed around did not exist. `MainViewModel` now calls `refreshUpcoming()`
  directly, which fixes this feature but leaves the force-stop gap open — recorded in §17b as outstanding.
- **A non-exported receiver cannot be driven by `adb broadcast`.** The first attempt to fire the skip action
  from the shell reported `result=0` and did nothing, exactly as the 0.1.5 entry describes for
  `DebugAlarmReceiver`. `UpcomingAlarmReceiver` is correctly `exported="false"`, so the action was verified
  by tapping the real button in the shade via `uiautomator` instead.
- **Recompute is stateless, so a swiped-away notification comes back.** Cancel-then-recompute re-posts the
  same alarm's notification at the next recompute. A swipe does not promote the second alarm, which is the
  intended rule, but it does not suppress the first one permanently either.
- Lint reports 24 warnings, not the 18 `AGENTS.md` documents. All 24 sit in `build.gradle.kts`,
  `libs.versions.toml` and the two documented `AndroidManifest.xml` attributes; the drift is upstream
  releases, not this work.

## 2026-08-26 — Xiaomi 14T (`2406APNFAG`), Android 16 (API 36), HyperOS `OS3.0.302.0.WNEMIXM`, release 0.1.5

Lane: mixed (human-eyes + adb capture)   Build: 0.1.5 (105)   Package: `dev.upyet`

**The first physical-device verification in this repository's history.** Release build, installed over the
existing install with `-r` so the owner's real alarms and history survived. Device is the owner's daily
driver; UI language Spanish. Security patch 2026-06-01, battery 100 % and charging. USB debugging attached
for capture only — no debug build, no debug receiver, no instrumented tests. The alarm was created by hand
in the UI, the way a user creates one.

| Scenario | Result | Notes |
|---|---|---|
| `core-path` | PASS | Screen woke by itself; ringing screen appeared **above** the lock screen; recording ran; dismiss worked first tap; **device stayed locked after dismissal** (`isKeyguardShowing=true` confirmed after the fact) |
| `history-thumbnail` | PASS | Owner confirmed by eye that the history row shows a real, recognisable frame. This is the assertion no automation can make |

Event timeline from device logcat, alarm scheduled for 03:12:00.000:

| Time | Event |
|---|---|
| 03:12:00.032 | `alarm_triggered alarmId=6,kind=MAIN` — 32 ms after the scheduled instant |
| 03:12:00.104 | `playback_service_started alarmId=6,unlocked=true` |
| 03:12:00.456 | `camera_bind_requested` |
| 03:12:01.129 | `recording_started` — 1.1 s after trigger |
| 03:12:16.463 | `alarm_dismissed alarmId=6` |
| 03:12:16.563 | `recording_finalized status=RECORDED,error=null` |
| 03:12:16.761 | `thumbnail_extracted ok=true` |

### Findings

- **Vibration is requested without `VibrationAttributes`, so the platform classifies it as
  `USAGE_UNKNOWN`.** Observed: `attrs=VibrationAttributes{mUsage=UNKNOWN, mAudioUsage=USAGE_UNKNOWN}` and
  HyperOS then running `weakenVibrationIfNecessary`. `AlarmVibrator` calls the single-argument
  `vibrate(VibrationEffect)` overload. The vibration did fire (`Vibrator on for timeoutMs: 700`, amplitude
  1.0, repeating until `CANCELLED_BY_USER` at dismissal) and the owner did not notice it over the
  ringtone. It matters anyway: an `USAGE_UNKNOWN` vibration is suppressed under Do Not Disturb, where an
  `USAGE_ALARM` one is exempt, and DND at night is the normal state for an alarm clock's user. Not fixed
  in this pass; it is an application defect, not a test defect.
- **The platform confirmed the camera-ownership architecture is load-bearing.** HyperOS logged
  `Foreground service started from background can not have location/camera/microphone access: service
  dev.upyet/.alarm.playback.AlarmPlaybackService`, and evidence recorded anyway — because `RingingActivity`
  owns the camera, not the service. Had the service owned it, evidence would be silently dead on Android 16.
- **`setAlarmClock` semantics confirmed on hardware before the alarm fired.** `dumpsys alarm` showed the
  occurrence under `Next alarm clock information` with a `showIntent`, and under `Next wake from idle` —
  so HyperOS, the OEM most likely to defer it, had registered it to fire through Doze.
- **HyperOS setup corrections**, applied to `docs/TESTING.md`: Autostart no longer exists as a setting on
  HyperOS 3.0. The battery entry exists under a different label. "Other permissions" contains three items,
  of which "Open new windows while in background" is the full-screen-intent gate and was already enabled.
- Notification records showed `numEnqueuedByApp=15, numPostedByApp=5` before the pass. Not investigated;
  noted in case it recurs.
- `NotificationVibratorHelper: Error creating vibration waveform with pattern: [0]` is HyperOS noise from a
  channel with `enableVibration(false)`, which is deliberate — the service owns vibration. Not a defect.

## 2026-08-26 — emulator (`sdk_gphone64_x86_64`), Android 14 (API 34), debug 0.1.5 — instrumented suite green

Lane: automated   Build: 0.1.5 (105)   Package: `dev.upyet.debug`

Re-run after fixing the three failures recorded in the entry below. **18 tests, 0 failures, 0 errors.**

| Scenario | Result | Notes |
|---|---|---|
| `core-path` | PASS | Unchanged from the previous run |

### Findings

- All three previously failing instrumented tests were diagnosed to root cause and fixed. None was an
  application bug; all three were defects in the tests or the build.
  - `AlarmListScreenTest` asserted a hardcoded `"7:30 AM"`. ICU 72, which shipped in Android 14, changed
    the space before the day period from U+0020 to U+202F NARROW NO-BREAK SPACE. Confirmed on-device: the
    formatter emits `U+0037 U+003A U+0033 U+0030 U+202F U+0041 U+004D`. The app was right; the literal was
    wrong, and would have been wrong again on any other locale. The test now formats its expectation.
  - `NavigationTest` drove a `NavHostController` from the instrumentation thread. Setting `graph`
    registers a lifecycle observer, which `LifecycleRegistry` requires on the main thread. Now wrapped in
    `runOnMainSync`, which is also how the app uses a NavController.
  - `MigrationSmokeTest` hit `AbstractMethodError` from kotlinx-serialization. `room-migration:2.8.4`
    needs serialization 1.8.1 to parse the exported schema JSON; `androidx.savedstate:1.4.0`, pulled
    through lifecycle and navigation, requires 1.7.3 *strictly*, and that strict constraint silently
    downgraded Room's requirement. Fixed by forcing serialization to 1.8.1 on the androidTest classpaths
    only. The application's own classpaths still resolve 1.7.3 with no conflict and are unchanged.
- Lint's deliberate-warning count moved from 14 to 18: the four new `NewerVersionAvailable` entries are
  the serialization pins above. `AGENTS.md` records the new count and why.

## 2026-08-26 — emulator (`sdk_gphone64_x86_64`), Android 14 (API 34), debug 0.1.5

Lane: automated   Build: 0.1.5 (105)   Package: `dev.upyet.debug`

Emulator run. Not a physical-device result: `core-path` is a `physical` scenario, so this is a regression
gate only. The AVD was started with `-camera-front emulated`; with the AVD default of
`hw.camera.front=none` no evidence assertion is exercisable at all.

| Scenario | Result | Notes |
|---|---|---|
| `core-path` | PASS | `CorePathFlowTest`, on the emulator. Alarm fired, screen woke, `RingingActivity` took the focused window, CameraX reported Start, ≥3 s recorded, dismiss worked, segment finalized `RECORDED` with a non-empty file. The keyguard claim was **not** exercised — no secure lock screen on this AVD, which the test logs rather than passing over |
| everything else | SKIPPED | Not attempted in this pass. The `emulator-ok` adb-lane scenarios (`doze`, `clock-change`, `timezone-change`, `low-storage`, `retention-cleanup`) remain unrun |

### Findings

- **Three instrumented tests fail on this emulator, and they are not new.** `AlarmListScreenTest.rendersAlarmTimeAndLabel`
  (expects `7:30 AM`), `MigrationSmokeTest.opensVersionOne` (`AbstractMethodError` from
  kotlinx-serialization), `NavigationTest.destinationsAreReachable` (`addObserver must be called on the
  main thread`). Confirmed pre-existing by running them at `3f23ad9`, which predates both the history
  thumbnail work and this harness: identical failures, same three tests. Suite total 18 tests, 3 failures.
  Cause not yet diagnosed; none of the three touches the alarm path.
- **The documented `am broadcast` scheduling recipe was dead.** `DebugAlarmReceiver` was
  `exported="false"`, so broadcasts from the shell uid were silently dropped — the command reported
  `result=0` and no alarm was ever scheduled. It is now exported under the `DUMP` permission, which only
  shell and system hold. The receiver exists solely in the debug source set.
- **`DebugAlarmReceiver` hardcoded `evidenceEnabled = false`,** so an alarm scheduled the documented way
  could never record evidence. Now an `--ez evidence` extra, defaulting true.
- **The AVD had no front camera.** The app binds `DEFAULT_FRONT_CAMERA` only, so the first run failed with
  `CAMERA_UNAVAILABLE`. Now a documented prerequisite.

