# MyAlarm

A native Android alarm clock that records **video evidence that you actually dismissed the alarm**.

When an alarm rings, MyAlarm shows its own alarm screen over the lock screen and — only while that screen
is visible — records a short silent video from the front camera. Later you can open the app and answer the
question *"did I actually wake up and dismiss that alarm?"*

Everything stays on the device: no account, no backend, no analytics, no network permission at all.

## Design in one paragraph

Alarms are real exact alarms (`AlarmManager.setAlarmClock`), scheduled one occurrence at a time from an
explicit recurrence rule evaluated in local wall-clock time. When one fires, a `mediaPlayback` foreground
service owns the ringing (audio, vibration, wake lock, notification) so the alarm is independent of the UI,
and a full-screen-intent notification opens `RingingActivity` over the keyguard. CameraX records evidence
bound to that Activity's lifecycle; a camera, storage, database or UI failure is recorded as a status and
never blocks dismissal. Alarm-critical state is mirrored into device-protected storage so alarms still ring
after a reboot with no unlock.

Read [`AGENTS.md`](AGENTS.md) for the binding engineering rules, [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
for how and why it is built this way, and [`docs/TESTING.md`](docs/TESTING.md) for the test matrix.

## Build

Requires JDK 17+ and the Android SDK (compileSdk 37, build-tools 36.0.0).

```bash
./gradlew spotlessApply     # format
./gradlew spotlessCheck test lint assembleDebug
./gradlew installDebug
./gradlew connectedDebugAndroidTest   # needs a device or emulator
```

Signed release APK (R8-minified, no debug tooling):

```bash
./scripts/build-android-release.sh
```

It creates `~/.config/my-alarm/android-signing/{android-release.jks,credentials.env}` on first run
(PKCS12, RSA 4096, random password), builds `assembleRelease`, and verifies the signature. Later runs reuse
the same key, so upgrades install over each other. In CI, export `MY_ALARM_ANDROID_KEYSTORE`,
`MY_ALARM_ANDROID_KEY_ALIAS` and `MY_ALARM_ANDROID_KEYSTORE_PASSWORD` instead; without them
`assembleRelease` still builds, but unsigned.

Debug builds accept an adb-triggered alarm for testing:

```bash
adb shell am broadcast -a dev.myalarm.debug.SCHEDULE --ei seconds 60 -p dev.myalarm.debug
```

## Status

Compile-verified, unit-tested and lint-clean. The end-to-end alarm, lock-screen and camera behaviour has
**not** been verified on a physical device in this repository's history — see the matrix in
[`docs/TESTING.md`](docs/TESTING.md) for what still needs to be run on real hardware.

## Stack

Kotlin, Jetpack Compose + Material 3, Hilt, Room, DataStore, CameraX, Media3, `java.time`,
AGP 9 / Gradle 9 with a version catalog, Spotless + ktlint, Android Lint.
