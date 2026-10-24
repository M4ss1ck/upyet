# Research: scheduling a timer so it rings reliably and survives reboot

Answers [issue #6](https://github.com/M4ss1ck/upyet/issues/6). Researched 2026-10-24 against primary
sources only. Every claim below cites the source it came from; anything not confirmed in a source is
marked **unverified**.

Sources read:

* AOSP DeskClock, `platform/packages/apps/DeskClock`, branch `main` at commit
  [`04e481f`](https://android.googlesource.com/platform/packages/apps/DeskClock/+/04e481f37e0b52b74c5a5c7b78b662d1f94e3478)
  (2025-02-27, the latest commit on `main` at time of reading). Files cited by path below.
* AOSP `frameworks/base`, branch `main`:
  [`AlarmManager.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/apex/jobscheduler/framework/java/android/app/AlarmManager.java)
  and
  [`AlarmManagerService.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java).
* developer.android.com: [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule),
  [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types),
  [Restrictions on starting a foreground service from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start),
  [Android 15 behavior changes](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed).
* Google Play: [Use of exact alarm permission](https://support.google.com/googleplay/android-developer/answer/13161072).
* source.android.com: [Full-screen intent limits](https://source.android.com/docs/core/permissions/fsi-limits).

## Answer in one paragraph

Schedule a timer's end with `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, …)`, not
`setAlarmClock()`. That is exactly what AOSP DeskClock does, it fires through Doze and App Standby, it
grants the same background foreground-service start as `setAlarmClock()`, and it keeps the timer out of
the system's "next alarm" (status-bar icon, lock screen, `getNextAlarmClock()`). Persist both a
wall-clock and an elapsed-realtime anchor so the remaining time can be recomputed after reboot, and
restore timers from the Direct-Boot-aware `LOCKED_BOOT_COMPLETED` receiver. `USE_EXACT_ALARM`
explicitly covers timer apps under Play policy. Ring with the same `mediaPlayback` foreground service as
alarms, but never start it from the boot receiver: on targetSdk 35+ a `BOOT_COMPLETED` receiver may not
start a `mediaPlayback` FGS, so a timer that expired while the phone was off must be re-armed as an
immediate exact alarm (or reported as missed), not rung from the boot path.

## 1. Which AlarmManager API: `setAlarmClock()` or `setExactAndAllowWhileIdle()`

### What `setAlarmClock()` means to the system

* Javadoc: it schedules "an alarm that represents an alarm clock", and "the system will typically also
  use the information supplied here to tell the user about this upcoming alarm if appropriate." It
  "implies `RTC_WAKEUP`". ([AlarmManager.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/apex/jobscheduler/framework/java/android/app/AlarmManager.java), `setAlarmClock` javadoc)
* `getNextAlarmClock()` returns the next alarm "scheduled by any application using the `setAlarmClock`
  method", so any other app reading it (sleep trackers, launchers, the lock screen) would see the timer
  as the user's next alarm. (same file, `getNextAlarmClock` javadoc)
* The javadoc warns these alarms "can be extremely expensive on battery use and should only be used for
  their intended purpose", because the system does prep-work to leave idle early. (same javadoc)
* In the service, an alarm with an `AlarmClockInfo` gets `FLAG_WAKE_FROM_IDLE` and a zero window.
  ([AlarmManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java), `setImpl`: "If this alarm is for an alarm clock, then it must be exact and we will use it to wake early from idle if needed.")

Using it for a timer would therefore show a 4-minute pasta timer as "next alarm 18:04" in the status bar
and to every app that asks. That contradicts the glossary's split between an **Alarm** (a wall-clock
rule) and a timer, and it would also interfere with UpYet's own "next alarm" surfaces.

### What `setExactAndAllowWhileIdle()` guarantees

* Javadoc: "this alarm will be allowed to execute even when the system is in low-power idle modes", and
  on dispatch "the app will also be added to the system's temporary power exemption list for
  approximately 10 seconds". ([AlarmManager.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/apex/jobscheduler/framework/java/android/app/AlarmManager.java), `setExactAndAllowWhileIdle` javadoc)
* Same javadoc, for both `setAlarmClock` and `setExactAndAllowWhileIdle`: "Alarms scheduled via this API
  will be allowed to start a foreground service even if the app is in the background." So the ringing
  FGS start is equally permitted with either API. developer.android.com lists the same exemption: "Your
  app invokes an exact alarm to complete an action that the user requests."
  ([restrictions-bg-start](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start))
* **Rate limit.** The javadoc still describes the legacy limit ("not … more than about every minute …
  in low-power idle modes … such as 15 minutes"). The current service code is what applies: for apps
  targeting S+, `ALLOW_WHILE_IDLE` alarms are limited to `DEFAULT_ALLOW_WHILE_IDLE_QUOTA = 72` per
  `DEFAULT_ALLOW_WHILE_IDLE_WINDOW = 1 hour` per package while the device is idle; pre-S apps get the
  compat quota of 7 per hour. (AlarmManagerService.java, `Constants` and `adjustDeliveryTimeBasedOnDeviceIdle`)
  A timer feature schedules one alarm per timer end, so 72/hour is not a practical constraint.
* **App Standby.** `isExemptFromAppStandby()` returns true for any alarm with `FLAG_ALLOW_WHILE_IDLE`,
  so standby buckets do not defer it. (AlarmManagerService.java, `isExemptFromAppStandby`)
* **Precision while idle.** The javadoc says "the OS will allow itself more flexibility for scheduling
  these alarms than regular exact alarms … When the device is idle it may take even more liberties". In
  the service, an idle device delivers it immediately while the app is under quota. **Unverified**: how
  late a `setExactAndAllowWhileIdle` timer actually fires in deep Doze on real hardware. Settle with an
  `adb shell dumpsys deviceidle force-idle` run on a device, logging scheduled vs. delivered elapsed time.

### What AOSP DeskClock does

`TimerModel.updateAlarmManager()` finds the running timer with the earliest `expirationTime` and arms a
single `FLAG_ONE_SHOT` service `PendingIntent` (request code 0) for it; with no running timer it cancels
that `PendingIntent`. It is called from `doUpdateTimer()` on every timer state change, so there is
always exactly one armed timer alarm. The arming call is:

```kotlin
fun schedulePendingIntent(am: AlarmManager, triggerTime: Long, pi: PendingIntent) {
    if (Utils.isMOrLater) {
        // Ensure the timer fires even if the device is dozing.
        am.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, triggerTime, pi)
    } else {
        am.setExact(ELAPSED_REALTIME_WAKEUP, triggerTime, pi)
    }
}
```

([`src/com/android/deskclock/data/TimerModel.kt`](https://android.googlesource.com/platform/packages/apps/DeskClock/+/04e481f37e0b52b74c5a5c7b78b662d1f94e3478/src/com/android/deskclock/data/TimerModel.kt), `updateAlarmManager` and `schedulePendingIntent`)

`setAlarmClock` appears in DeskClock only in `Utils.updateNextAlarm()`, used for alarm instances; the
timer code never calls it. ([`src/com/android/deskclock/Utils.kt`](https://android.googlesource.com/platform/packages/apps/DeskClock/+/04e481f37e0b52b74c5a5c7b78b662d1f94e3478/src/com/android/deskclock/Utils.kt))

### Clock base

developer.android.com: "elapsed real time is suited to setting an alarm based on the passage of time …
since it isn't affected by time zone or locale … We recommend that you use an 'elapsed real time' alarm
if you can." ([Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule))
A timer is a duration, so `ELAPSED_REALTIME_WAKEUP` is correct: a manual clock change or `TIME_SET` /
`TIMEZONE_CHANGED` does not move it. This is the mirror image of AGENTS.md section 6, where alarms are
wall-clock rules and must follow time-zone changes. `setAlarmClock` would force `RTC_WAKEUP` and make a
timer jump when the user changes the clock.

## 2. Reboot and Direct Boot

* Alarms do not survive reboot: "Registered alarms are retained while the device is asleep … but will be
  cleared if it is turned off and rebooted." (AlarmManager.java class javadoc) and "By default, all
  alarms are canceled when a device shuts down." ([Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule))
* `ELAPSED_REALTIME` restarts at zero on boot, so a stored elapsed expiry is meaningless after reboot.
  DeskClock's `Timer` therefore stores both `lastStartTime` (elapsed realtime) and `lastWallClockTime`.
  `Timer.updateAfterReboot()` computes `delta = max(0, wallClock - lastWallClockTime)`, subtracts it from
  the remaining time, and re-anchors both clocks to "now".
  ([`src/com/android/deskclock/data/Timer.kt`](https://android.googlesource.com/platform/packages/apps/DeskClock/+/04e481f37e0b52b74c5a5c7b78b662d1f94e3478/src/com/android/deskclock/data/Timer.kt))
* `TimerModel.doUpdateAfterRebootTimer()` marks a running timer **missed** when its recomputed
  remaining time is below `MISSED_THRESHOLD = -MINUTE_IN_MILLIS` (more than one minute overdue);
  otherwise it stays running or expired and rings. It then goes through `doUpdateTimer()`, which re-arms
  AlarmManager. (TimerModel.kt)
* `Timer.updateAfterTimeSet()` handles `TIME_SET` by re-anchoring from elapsed time and ignores negative
  deltas, which "typically happen following reboots when TIME_SET is broadcast before BOOT_COMPLETED".
  (Timer.kt)
* `AlarmInitReceiver` calls `DataModel.updateAfterReboot()` on `LOCKED_BOOT_COMPLETED` on N+ ("we're
  interested in the boot completed event that is sent while the user is still locked, so that we can
  schedule alarms"), and `updateAfterTimeSet()` on `TIME_CHANGED`. The receiver and `TimerService` are
  `directBootAware="true"`.
  ([`src/com/android/deskclock/AlarmInitReceiver.kt`](https://android.googlesource.com/platform/packages/apps/DeskClock/+/04e481f37e0b52b74c5a5c7b78b662d1f94e3478/src/com/android/deskclock/AlarmInitReceiver.kt),
  [`AndroidManifest.xml`](https://android.googlesource.com/platform/packages/apps/DeskClock/+/04e481f37e0b52b74c5a5c7b78b662d1f94e3478/AndroidManifest.xml))
* Timer state lives in `SharedPreferences` opened from `createDeviceProtectedStorageContext()` on N+, so
  it is readable before first unlock.
  ([`src/com/android/deskclock/DeskClockApplication.kt`](https://android.googlesource.com/platform/packages/apps/DeskClock/+/04e481f37e0b52b74c5a5c7b78b662d1f94e3478/src/com/android/deskclock/DeskClockApplication.kt))

For UpYet this maps onto existing rules: timer state that the boot path needs belongs in the
device-protected mirror (AGENTS.md section 11), not in Room, and restoration runs from the existing
`BootReceiver` on `LOCKED_BOOT_COMPLETED`.

Caveat on DeskClock's wall-clock delta: if the user or network changes the wall clock while the phone is
off, the computed delta is wrong. There is no monotonic clock that survives a power cycle, so this is a
platform limit, not a DeskClock bug. **Unverified**: whether any public API exposes a reboot-surviving
monotonic time; none was found in the sources read.

## 3. Foreground-service type for timer ringing

DeskClock is no guide here: its manifest declares `targetSdkVersion="30"` and `TimerService` has no
`foregroundServiceType`, so it predates the Android 14 type requirement. (DeskClock `AndroidManifest.xml`)

Options from [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types):

| Type | Fit for timer ringing |
|---|---|
| `mediaPlayback` | "Continue audio or video playback from the background." Same type UpYet's `AlarmPlaybackService` already uses. Restriction: "Apps that target Android 15 or higher are not allowed to launch a media playback foreground service from a `BOOT_COMPLETED` broadcast receiver." |
| `shortService` | "Can only run for a short period of time (about 3 minutes)", then `onTimeout()`. A timer that rings until dismissed or until UpYet's ring timeout can exceed that. Not suitable. |
| `systemExempted` | Allowed for "Apps holding `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` permission", but described as "Reserved for system applications and specific system integrations". UpYet qualifies technically. Not on Android 15's boot-receiver blocklist. |

The Android 15 `BOOT_COMPLETED` blocklist is `dataSync`, `camera`, `mediaPlayback`, `phoneCall`,
`mediaProjection`, `microphone`; violating it throws `ForegroundServiceStartNotAllowedException`.
([Android 15 behavior changes](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed))
UpYet targets SDK 36, so this applies.

Recommendation: reuse `mediaPlayback`, consistent with alarms, and make sure the boot path never starts
it. When a timer is found already expired (within the one-minute grace) at `LOCKED_BOOT_COMPLETED`, arm
an exact alarm for "now" and let `AlarmReceiver`-style delivery start the service, because exact-alarm
delivery carries the FGS start exemption quoted in section 1. **Unverified**: that an FGS started from an
alarm that fires seconds after boot is not treated as a `BOOT_COMPLETED`-originated start. The docs tie
the restriction to the receiver, which suggests it is fine; settle it on an Android 15+ device with
`adb shell am compat enable FGS_BOOT_COMPLETED_RESTRICTIONS dev.upyet.debug` (debug build) and a reboot while a timer
is about to expire. The same risk applies today to any alarm UpYet would ring straight from
`BootReceiver`; worth checking the alarm path in the same run.

## 4. Does `USE_EXACT_ALARM` cover timers?

Yes. The Play policy lists acceptable core functionality as "The app is an alarm or timer app" and "The
app is a calendar app that shows event notifications"; the summary says "dedicated alarm, timer, or
calendar applications". ([Play policy answer 13161072](https://support.google.com/googleplay/android-developer/answer/13161072))
The developer guide adds that `USE_EXACT_ALARM` is "Granted automatically" and "Cannot be revoked by the
user". ([Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule))
Both `setAlarmClock` and `setExactAndAllowWhileIdle` require the exact-alarm permission on S+
(AlarmManager.java javadoc), and UpYet already declares `USE_EXACT_ALARM` plus `SCHEDULE_EXACT_ALARM`
with `maxSdkVersion="32"`, so no manifest change is needed.

The policy wording supports UpYet as an alarm app that also offers timers. It does not say what happens
when a timer is a secondary feature of an app whose core is something else, which is not UpYet's case.

## Recommendation

1. Arm timer ends with `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, end, pi)`. Never
   `setAlarmClock()` for timers: it publishes the timer as the user's next alarm and forces `RTC_WAKEUP`.
   This needs an explicit carve-out in AGENTS.md (the header block and section 6), which today says "Only
   `AlarmManager.setAlarmClock()` schedules real alarm occurrences": a timer is not an alarm occurrence,
   but the wording should say so before code lands, so nobody "fixes" the timer onto `setAlarmClock`.
2. Use a deterministic, timer-specific `PendingIntent` identity distinct from alarm identities
   (AGENTS.md section 6 rule). DeskClock's single "earliest running timer" alarm is simpler than one
   alarm per timer and costs one quota slot; either works under the 72/hour quota.
3. Persist each running timer as remaining duration plus both anchors (elapsed realtime and wall clock)
   in device-protected storage. On `LOCKED_BOOT_COMPLETED` recompute from the wall-clock delta; on
   `TIME_SET` re-anchor from elapsed time and ignore negative deltas.
4. After reboot, a timer overdue by more than a grace period (DeskClock: 1 minute) is recorded as missed
   and shown, not rung. One overdue by less is re-armed as an immediate exact alarm rather than rung
   from `BootReceiver`.
5. Ring with a `mediaPlayback` foreground service started from the exact-alarm delivery path, with
   `USAGE_ALARM` audio (DeskClock's `TimerKlaxon` uses `USAGE_ALARM` / `CONTENT_TYPE_SONIFICATION`) and a
   full-screen-intent notification (DeskClock's `TimerNotificationBuilder` uses `CATEGORY_ALARM` and
   `setFullScreenIntent`).
6. Keep `USE_EXACT_ALARM`; Play policy names timer apps explicitly.

## Open questions

* **Precision in deep Doze.** How late does `setExactAndAllowWhileIdle` fire under forced idle on real
  hardware compared with `setAlarmClock`? Device test, see section 1.
* **Boot-adjacent FGS start.** Confirm on Android 15+ that ringing from an alarm armed in `BootReceiver`
  does not trip the `BOOT_COMPLETED` FGS restriction. Device test, see section 3.
* **Does a timer record evidence?** Out of scope for scheduling, but it decides whether timer ringing
  goes through `RingingActivity` and CameraX or a lighter screen. Product decision.
* **Full-screen intent for timers.** On Android 14+ `USE_FULL_SCREEN_INTENT` is granted by default only
  to apps "that provide calling and alarm functionalities"
  ([FSI limits](https://source.android.com/docs/core/permissions/fsi-limits)). UpYet already qualifies as
  an alarm app, so a timer using FSI should be covered, but the page does not mention timers.
  Unverified for Play's declaration review.
* **Multiple concurrent timers.** One alarm for the earliest end (DeskClock) or one per timer? Decide
  with the timer data model.
* **`systemExempted` instead of `mediaPlayback`.** It would sidestep the boot restriction, but its
  description reserves it for system integrations and Play's FGS declaration review may reject it.
  Not recommended without a Play policy check.
