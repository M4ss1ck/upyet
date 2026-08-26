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

