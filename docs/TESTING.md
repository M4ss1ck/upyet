# MyAlarm — testing

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

Priority 1 — the end-to-end path:

```text
create alarm for +2 minutes → close app → lock phone → wait
→ alarm triggers → sound starts → screen wakes
→ RingingActivity appears above the keyguard
→ CameraX initializes → recording starts → wait 10 s → dismiss
→ recording finalizes → sound stops → phone remains locked
→ unlock, open app → occurrence in history → evidence video plays
```

Then, each recorded as pass/fail with device and API level:

| # | Scenario | Expected |
|---|---|---|
| 1 | Doze (see §5) | alarm fires on time |
| 2 | App process killed normally | alarm fires |
| 3 | Device already unlocked and in use | heads-up alarm notification; tapping opens ringing screen |
| 4 | Camera permission denied | alarm rings, dismiss works, status `Camera permission denied` |
| 5 | Camera permission revoked after alarm creation | same as #4, no crash |
| 6 | Camera in use by another app | status `Camera unavailable`, alarm unaffected |
| 7 | Recording initialization failure | status `Camera initialization failed` |
| 8 | Screen turned off while ringing | audio continues, segment ends, new segment on re-show |
| 9 | Activity backgrounded and returned | two segments recorded for one occurrence |
| 10 | Incoming call while ringing | alarm survives; evidence segment ends cleanly |
| 11 | Multiple alarms in quick succession | each occurrence recorded separately |
| 12 | Snooze | ringing ends, evidence finalized, snooze alarm fires later, occurrences linked |
| 13 | Reboot after scheduling (then unlock) | alarm still fires |
| 14 | Reboot, never unlock (§6) | alarm still fires; evidence may report Direct Boot unavailable |
| 15 | Manual clock change | next occurrence recomputed |
| 16 | Timezone change | alarms keep local wall-clock semantics |
| 17 | DST transition | no double or skipped firing |
| 18 | Exact-alarm access removed / restored | reliability screen warns; alarms rescheduled on restore |
| 19 | Full-screen-intent access unavailable | notification still routes to ringing screen |
| 20 | Notifications disabled | reliability screen warns with settings action |
| 21 | Low storage | evidence status `Storage error`, alarm unaffected |
| 22 | Delete occurrence | metadata and video files removed |
| 23 | Retention cleanup | segments older than the policy are removed, active ones never |

## 5. ADB recipes

```bash
# Install and launch
./gradlew installDebug
# Or the signed release build (package dev.myalarm, no debug tooling):
./scripts/build-android-release.sh && adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n dev.myalarm.debug/dev.myalarm.MainActivity

# Debug-only: schedule an alarm N seconds from now (debug builds only)
adb shell am broadcast -a dev.myalarm.debug.SCHEDULE --ei seconds 60 -p dev.myalarm.debug

# Kill the app process normally (alarms must survive this)
adb shell am kill dev.myalarm.debug

# Force stop (alarms are cancelled by the platform; app must recover on next launch)
adb shell am force-stop dev.myalarm.debug

# Doze
adb shell dumpsys deviceidle enable
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce && adb shell dumpsys deviceidle disable

# Inspect scheduled alarms
adb shell dumpsys alarm | grep -A 12 dev.myalarm

# Screen off / lock
adb shell input keyevent 26

# Notification and permission state
adb shell dumpsys notification --noredact | grep -A 20 dev.myalarm
adb shell pm revoke dev.myalarm.debug android.permission.CAMERA
adb shell pm grant  dev.myalarm.debug android.permission.CAMERA

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
adb shell run-as dev.myalarm.debug ls /data/user_de/0/dev.myalarm.debug/shared_prefs
```

## 7. Reporting rules

State clearly which level of verification a claim has: *compile-verified*, *unit-tested*,
*emulator-tested*, *physical-device-tested*, or *reasoned but unverified*. Device-matrix results belong
in the PR/commit description with device model and API level.
