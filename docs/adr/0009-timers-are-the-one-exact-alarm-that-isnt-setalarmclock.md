# Timers are the one exact alarm that isn't `setAlarmClock`

`AGENTS.md` §6 says only `AlarmManager.setAlarmClock()` schedules a real alarm occurrence, and
`0008-a-timer-is-five-states-and-one-exact-alarm.md` gave a timer one exact alarm without saying which
call makes it. The answer is `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)`. Timers are a closed
exception to the `setAlarmClock()` rule: only timers, only that API, nothing else. This ADR records why,
from the research in issue #6 (`docs/research/timer-scheduling.md` on branch `research/timer-scheduling`).

**AOSP DeskClock does exactly this, in exactly this place.** `TimerModel.schedulePendingIntent` in
DeskClock schedules a timer's expiry with `setExactAndAllowWhileIdle` on `ELAPSED_REALTIME_WAKEUP`, not
with `setAlarmClock`. UpYet's timer copies that feature, so following its scheduling call is following the
precedent rather than inventing a deviation.

**`setAlarmClock()` would spill the timer into the alarm surface.** An alarm scheduled that way becomes
the system's next alarm: it is what `getNextAlarmClock()` returns, and what the status bar and the lock
screen show as the next thing that will wake the user. A countdown is not a wake-up time. Someone who set
an alarm for 07:00 must keep seeing 07:00 there, not "4 minutes". It also pins `RTC_WAKEUP`, so a clock
change or a timezone change would move the countdown; `ELAPSED_REALTIME` counts time actually elapsed and
is immune to both.

**Nothing is lost by dropping to `setExactAndAllowWhileIdle`.** It fires through Doze and App Standby,
and its quota (72 deliveries per hour for S+ targets) is far beyond anything a timer can need. Its javadoc
grants the same background foreground-service start authorisation as `setAlarmClock()`, which is what
lets `TimerPlaybackService` start when a timer expires with no UI on screen. `USE_EXACT_ALARM` covers it:
Play's policy states the permission is for an "alarm or timer app", and UpYet is one, so this is a second
use of a permission the app already declares for a genuine reason.

**Reboot is the hard case, and it is why boot re-arms instead of ringing.** A reboot clears every alarm
and resets `elapsedRealtime`. The elapsed anchor and the wall-clock anchor that ADR 0008 stores in
device-protected storage are what let a timer survive it: at `LOCKED_BOOT_COMPLETED` the remaining time
is recomputed from the wall-clock delta, a timer more than a minute overdue becomes `Missed`, and a timer
still in the future gets a fresh exact alarm at its recomputed end. A timer due within the grace window at
boot is armed as an immediate exact alarm rather than rung from `BootReceiver`, because on targetSdk 35+ a
`BOOT_COMPLETED` receiver may not start a `mediaPlayback` foreground service. The service type stays
`mediaPlayback`, the one alarms already use; `shortService` would time out after about three minutes.

## Considered options

- **`setAlarmClock()` for timers too.** Rejected: one call, one mental model, and the exact-alarm
  permission and FGS authorisation come with it, but it publishes the timer as the system's next alarm
  (a lie on the lock screen) and ties the countdown to the wall clock.
- **`setExact()` or `setWindow()` on `RTC_WAKEUP`.** Rejected: same clock fragility as `setAlarmClock()`
  without the visibility problem, and `setExact` alone is throttled in Doze where
  `setExactAndAllowWhileIdle` is not.
- **Inexact `setAndAllowWhileIdle`.** Rejected: a countdown that rings whenever the platform feels like it
  is worse than an alarm that does, because the user is watching the remaining time.
- **An in-process timer, WorkManager or a foreground service that counts.** Rejected for the same reason
  alarms forbid them: Doze, process death and reboot all break them, and none is exact.

## Consequences

`AGENTS.md` §6 and its header block get a timer-scheduling subsection stating these rules (issue #11),
and §20's forbidden-list "timers" is narrowed to in-process timers (`Handler`, `java.util.Timer`,
coroutine `delay`) so it no longer forbids the platform mechanism it needs. No code has to change to
satisfy this ADR; it constrains the code that is about to be written.

Two claims remain reasoned but unverified and need a device: the precision a timer actually gets under
deep Doze, and whether an immediate exact alarm raised at `LOCKED_BOOT_COMPLETED` is allowed to start
`mediaPlayback` on current Android releases. Both are open in the research document.
