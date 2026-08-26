# UpYet — testing

Three layers: JVM unit tests, instrumented tests, and a manual device matrix. Alarm-clock correctness
cannot be established by unit tests alone, so the device matrix is part of the definition of done.

## 1. Commands

```bash
./gradlew spotlessApply             # format (ktlint via Spotless)
./gradlew spotlessCheck             # verify formatting
./gradlew test                      # JVM unit tests
./gradlew lint                      # Android Lint
./gradlew assembleDebug             # build
./gradlew connectedDebugAndroidTest # instrumented tests (device/emulator required)
```

Never skip or `@Ignore` a failing test to get a green run. If a failure is environment-specific, state
precisely which environment and why.

## 2. Unit tests (JVM, `app/src/test`)

| Area | Cases |
|---|---|
| `NextOccurrenceCalculator` | one-time before/after current time, daily, weekly next-day, weekly week rollover, single-weekday 7 days out, DST spring-forward gap (America/New_York, 02:30 on 2027-03-14), DST fall-back overlap (01:30 on 2027-11-07), identical wall clock in two zones |
| `SnoozeCalculator` | snooze offsets, minimum/maximum configured durations |
| Scheduling identity | request-code derivation is deterministic, stable and collision-free for main vs snooze |
| Alarm domain behaviour | enable/disable/delete/snooze/reschedule transitions, occurrence outcome transitions, evidence state transitions |
| Mappers | entity ↔ domain round trips including recurrence encoding and enum persistence |
| Retention | which segments a policy selects, and that in-progress segments are never selected |
| Reliability checks | each check's pass/fail evaluation from injected platform state |

## 3. Instrumented tests (`app/src/androidTest`)

* Room: database creation, DAO round trips, foreign-key/cascade behaviour, migration test harness
  (`MigrationTestHelper`) wired even while v1 has a single schema version.
* Repositories against an in-memory database.
* Compose: alarm list rendering and toggles, alarm editor input, ringing controls (dismiss/snooze
  present and clickable while the camera state reports failure), history list and evidence-status
  labels, permission-state UI on the reliability screen.
* Navigation between the main destinations.
* Receiver/service integration that can reasonably be exercised (scheduler → receiver intent extras,
  service command handling with a fake player).

## 4. Device / emulator matrix

### The core path

```text
create alarm for +2 minutes → close app → lock phone → wait
→ alarm triggers → sound starts → screen wakes
→ RingingActivity appears above the keyguard
→ CameraX initializes → recording starts → wait 10 s → dismiss
→ recording finalizes → sound stops → phone remains locked
→ unlock, open app → occurrence in history → evidence video plays
```

This is the product. Everything else in the matrix is a way it can go wrong.

### The matrix

Every scenario has a stable slug. Tests, log entries and commit messages refer to a scenario by its slug
and never by its position in this table, so rows can be added or reordered without invalidating a record.

* **Hardware** — where a result counts. `physical` means only a physical-device run is evidence, because
  the behaviour depends on keyguard implementation, OEM power management, full-screen-intent policy or a
  real camera sensor. `emulator-ok` means the emulator is the better instrument (Doze can be forced,
  storage can be filled, the clock and timezone can be set). `either` means both are equally valid.
  Running a `physical` scenario on the emulator is still useful as a regression gate; it is not
  verification, and the log must not record it as one.
* **Lane** — how it is executed. `automated` is the instrumented suite in
  `app/src/androidTest/java/dev/upyet/e2e/`. `adb` is a scripted but supervised shell sequence from §5.
  `human-eyes` is a person watching, because the assertion is about what a human perceives.

| Scenario | Hardware | Lane | Expected |
|---|---|---|---|
| `core-path` | physical | automated | the flow above, end to end |
| `doze` | emulator-ok | adb | alarm fires on time |
| `process-killed` | either | adb | alarm fires; see the lane note below |
| `device-in-use` | physical | human-eyes | heads-up alarm notification; tapping opens the ringing screen |
| `camera-denied` | either | adb | alarm rings, dismiss works, status `Camera permission denied` |
| `camera-revoked-mid-flight` | either | adb | same as `camera-denied`, no crash |
| `camera-in-use` | physical | human-eyes | status `Camera unavailable`, alarm unaffected |
| `camera-init-failure` | physical | human-eyes | status `Camera initialization failed` |
| `screen-off-while-ringing` | physical | automated | audio continues, segment ends, new segment on re-show |
| `backgrounded-resumed` | either | automated | two segments recorded for one occurrence |
| `incoming-call` | physical | human-eyes | alarm survives; evidence segment ends cleanly |
| `back-to-back-alarms` | either | adb | each occurrence recorded separately |
| `snooze` | either | automated | ringing ends, evidence finalized, snooze alarm fires later, occurrences linked |
| `reboot-unlocked` | either | adb | alarm still fires; see the lane note below |
| `reboot-no-unlock` | physical | adb | alarm still fires; evidence may report Direct Boot unavailable (§6) |
| `clock-change` | emulator-ok | adb | next occurrence recomputed |
| `timezone-change` | emulator-ok | adb | alarms keep local wall-clock semantics |
| `dst-transition` | emulator-ok | adb | no double or skipped firing |
| `exact-alarm-revoked` | either | adb | reliability screen warns; alarms rescheduled on restore |
| `fsi-unavailable` | physical | human-eyes | notification still routes to the ringing screen |
| `notifications-disabled` | either | adb | reliability screen warns with a settings action |
| `low-storage` | emulator-ok | adb | evidence status `Storage error`, alarm unaffected |
| `delete-occurrence` | either | human-eyes | metadata and video files removed |
| `retention-cleanup` | emulator-ok | human-eyes | segments older than the policy are removed, active ones never |
| `history-thumbnail` | either | human-eyes | after recording, the history row shows a real frame from the clip, not a placeholder; deleting the occurrence leaves no orphan `.jpg` in `noBackupFilesDir/evidence/` |

**Permanent `adb` lane.** Five scenarios can never become instrumented tests, all for the same reason:
instrumentation is hosted by the application process, so anything that ends that process ends the test
runner with it and leaves nothing to assert with.

| Scenario | What kills the runner |
|---|---|
| `process-killed` | `am kill` on the package under test |
| `reboot-unlocked`, `reboot-no-unlock` | the reboot |
| `camera-denied`, `camera-revoked-mid-flight` | `pm revoke` — the platform kills the app's process on revocation (verified on API 34: the pid is gone within seconds) |

`camera-denied` could in principle be asserted by an instrumented flow that never grants the permission in
the first place, but permission grants persist for the life of an install, so it would depend on running
before any flow that grants `CAMERA`. Test order is not a thing to build correctness on; it stays `adb`.

**Automated lane, today.** Only `core-path` currently exists as an instrumented flow
(`CorePathFlowTest`). `screen-off-while-ringing`, `backgrounded-resumed` and `snooze` are marked
`automated` because that is where they belong, not because the flow exists — `snooze` in particular needs
a shorter snooze interval than the product's minimum before a test can wait for the second alarm. Until a
flow is written, run the scenario by hand and record it as `human-eyes` in the log.

**What automation cannot decide.** `history-thumbnail` and the evidence produced by `core-path` both end
in a judgement no assertion can make: whether the frame shows an actual, recognisable person. A test can
prove a non-empty MP4 exists, was finalized, and yields a JPEG. A human has to look at it.

### Running a verification pass

**Prerequisites.** USB debugging on and authorised (`adb devices` shows the device, not `unauthorized`).
A secure lock screen (PIN) set — without one there is no keyguard, and every `physical` scenario that
rests on keyguard behaviour is meaningless; `CorePathFlowTest` detects this and logs that it did not
exercise the keyguard claim rather than pretending it did. Battery above ~30 % for anything involving
Doze. For the Xiaomi lane, the setup below applied first.

**On an emulator, give the AVD a front camera.** The app records with
`CameraSelector.DEFAULT_FRONT_CAMERA` and nothing else, and the AVD default is `hw.camera.front=none`.
With no front camera every evidence assertion is unexercisable and the ringing screen reports
`Front camera unavailable`. Either set `hw.camera.front=emulated` (or `webcam0`) in the AVD's
`config.ini`, or start the emulator with `-camera-front emulated`. Check it with
`adb shell dumpsys media.camera | grep -i "Facing"` — a front-facing device must be listed.

**Be explicit about the package.** Debug installs as `dev.upyet.debug`, release as `dev.upyet`, and both
can be installed at once. A pass exercises exactly one of them, and the log entry names which. Note that
`dev.upyet` is a prefix of `dev.upyet.debug`, so any `grep dev.upyet` matches both — the recipes in §5
anchor on the exact package for that reason.

**Android Studio route** (the interactive lane):

1. Run configuration `app`, device selector → the target device, Run.
2. Logcat filtered to `package:mine tag:UpYet` — every alarm-path event is a single structured line.
3. Running Devices for screen mirroring, so the ringing screen can be watched without unlocking the phone
   by hand. Mirroring does not substitute for looking at the physical screen when the scenario is about
   what the screen does above the keyguard.
4. Instrumented flows: right-click `app/src/androidTest/java/dev/upyet/e2e` → Run.

**CLI route** (the automated lane):

```bash
./gradlew installDebug
./gradlew connectedDebugAndroidTest                        # whole instrumented suite
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=dev.upyet.e2e   # e2e flows only
```

Reports land in `app/build/reports/androidTests/connected/`. Screenshots the flows capture are written to
the app's external files directory and their absolute path is logged as `e2e_screenshot`; pull them with
`adb pull` before the run finishes, because Gradle uninstalls both APKs when `connectedDebugAndroidTest`
completes and the directory goes with them.

**How the lanes interleave.** Automated first, because it is cheap and its failures are unambiguous. Then
the `adb` scenarios, one shell sequence at a time, reading `dumpsys`/`logcat` between steps. Then the
`human-eyes` scenarios last, in one sitting, because they need the person's full attention and they are
the only ones that can judge the evidence itself. Record the run as one log entry covering all three.

### The gate

| When | What must run |
|---|---|
| Every release | `core-path`, plus the smoke subset: `doze`, `process-killed`, `camera-denied`, `snooze`, `reboot-unlocked` |
| Once before v1 | The full matrix, on a physical device for every `physical` row |
| After v1 | Only the scenarios the changed subsystem re-triggers, per the map below |

| Subsystem changed | Scenarios re-triggered |
|---|---|
| Scheduling (`AlarmScheduler`, `AlarmReceiver`, `BootReceiver`, recurrence) | `core-path`, `doze`, `process-killed`, `back-to-back-alarms`, `reboot-unlocked`, `reboot-no-unlock`, `clock-change`, `timezone-change`, `dst-transition`, `exact-alarm-revoked` |
| Playback (`AlarmPlaybackService`, notifications, wake lock) | `core-path`, `device-in-use`, `incoming-call`, `snooze`, `fsi-unavailable`, `notifications-disabled` |
| Camera / evidence (`EvidenceCoordinator`, CameraX use cases) | `core-path`, `camera-denied`, `camera-revoked-mid-flight`, `camera-in-use`, `camera-init-failure`, `screen-off-while-ringing`, `backgrounded-resumed`, `history-thumbnail` |
| Storage (Room, `EvidenceFileStore`, retention) | `low-storage`, `delete-occurrence`, `retention-cleanup`, `history-thumbnail`, `reboot-no-unlock` |
| UI (Compose screens, navigation, reliability screen) | `core-path`, `device-in-use`, `exact-alarm-revoked`, `notifications-disabled`, `delete-occurrence` |

A scenario that is skipped is recorded as skipped, with the reason. Silence is not a pass.

### Xiaomi / HyperOS setup

HyperOS restricts background apps harder than stock Android, and its restrictions are the most likely
cause of "the alarm rang but the ringing screen never appeared". Apply all of these before a pass on that
device, then re-check them after a HyperOS update, which can reset them.

| Setting | Where | Why it matters |
|---|---|---|
| Autostart → on | Settings → Apps → UpYet → Autostart | Without it the app is not allowed to start itself after a reboot or a kill, and `BOOT_COMPLETED` never lands |
| Battery saver → **No restrictions** | Settings → Apps → UpYet → Battery saver | The default restricts background activity and can delay or drop an exact alarm |
| **Display pop-up windows while running in background** → on | Settings → Apps → UpYet → Other permissions | This is the one that gates full-screen-intent launches on HyperOS. With it off the alarm rings but `RingingActivity` never comes up over the lock screen |
| Lock the app in Recents | Recents → long-press the UpYet card → lock icon | Stops a Recents clear-all from force-stopping the app, which the platform does not announce and which cancels alarms |
| Notifications → on, importance high, **Show on lock screen** → on | Settings → Apps → UpYet → Notifications | The ringing notification is the full-screen intent's carrier; a demoted channel silently downgrades it to a heads-up |

These paths are a draft written from HyperOS documentation, not from the device. Correct them in place
during the first Xiaomi pass to match the menus actually seen, and record the HyperOS version in the log
entry — the paths move between versions.

Gaps that HyperOS exposes and the app could in principle detect (autostart state, the pop-up permission)
are noted here as candidates. They are not implemented, and the reliability screen does not claim to check
them.

## 5. ADB recipes

Every recipe names the exact package. `dev.upyet` is a prefix of `dev.upyet.debug`, so an unanchored
`grep dev.upyet` matches both installs and cannot tell you which one scheduled an alarm.

```bash
PKG=dev.upyet.debug     # or PKG=dev.upyet for a release pass

# Install and launch
./gradlew installDebug
# Or the signed release build (package dev.upyet, no debug tooling):
./scripts/build-android-release.sh && adb install -r app/build/outputs/apk/release/upyet-*-release.apk
adb shell am start -n "$PKG"/dev.upyet.MainActivity

# Debug-only: schedule an alarm N seconds from now, with or without evidence. The receiver is exported
# but guarded by DUMP, so the shell can reach it and installed apps cannot. It exists only in the debug
# source set; a release pass creates the alarm through the UI instead.
adb shell am broadcast -a dev.upyet.debug.SCHEDULE --ei seconds 60 --ez evidence true -p dev.upyet.debug

# Kill the app process normally (alarms must survive this)
adb shell am kill "$PKG"

# Force stop (alarms are cancelled by the platform; app must recover on next launch)
adb shell am force-stop "$PKG"

# Doze
adb shell dumpsys deviceidle enable
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce && adb shell dumpsys deviceidle disable

# Inspect scheduled alarms for exactly one package. The word boundary matters: without it the release
# package's rows appear in a debug pass and vice versa.
adb shell dumpsys alarm | grep -E -A 12 "\b${PKG}\b"

# Which window is focused - the machine-checkable form of "the ringing screen is above the keyguard"
adb shell dumpsys window | grep mCurrentFocus

# Screen state and a viewable frame of whatever is on it
adb shell input keyevent 26                      # screen off / lock
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml   # resource-id, text, bounds, clickable

# Structured alarm-path logging
adb logcat -s UpYet

# Notification and permission state
adb shell dumpsys notification --noredact | grep -E -A 20 "\b${PKG}\b"
adb shell pm revoke "$PKG" android.permission.CAMERA
adb shell pm grant  "$PKG" android.permission.CAMERA

# Time / timezone / DST
adb shell su 0 date MMDDhhmmYYYY.ss     # rooted emulator only
adb shell setprop persist.sys.timezone America/New_York

# Storage pressure (emulator)
adb shell dd if=/dev/zero of=/data/local/tmp/fill bs=1m count=4000
```

## 6. Direct Boot testing

```bash
# 1. Ensure a secure lock screen (PIN) is set on the device/emulator.
# 2. Schedule an alarm a few minutes out.
adb reboot
# 3. Do NOT unlock. Wait for the alarm time.
#    Expected: sound + vibration + ringing screen above the keyguard; snooze and dismiss work.
#    Evidence may be recorded as DIRECT_BOOT_UNAVAILABLE.
# 4. Unlock. The occurrence must appear in history after reconciliation.

# Inspect device-protected storage (rooted emulator)
adb shell run-as dev.upyet.debug ls /data/user_de/0/dev.upyet.debug/shared_prefs
```

## 7. Reporting rules

State clearly which level of verification a claim has: *compile-verified*, *unit-tested*,
*emulator-tested*, *physical-device-tested*, or *reasoned but unverified*. An emulator run of a `physical`
scenario is an emulator result and is never written up as a device result.

Every verification pass gets an entry in `docs/VERIFICATION-LOG.md`: what ran, on what hardware, against
which package, and what it did. That file is the record; a PR or commit description referring to device
results points at the log entry rather than restating it. A scenario that failed is a log entry with the
failure written down. It becomes a GitHub issue only when it blocks v1.
