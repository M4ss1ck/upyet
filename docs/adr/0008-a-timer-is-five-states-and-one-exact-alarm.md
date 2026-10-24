# A timer is five states and one exact alarm

**Status: partly superseded by ADR 0010 (evidence).** A timer can record evidence and leave an
**Occurrence** in History when its **Timer evidence** is on. Everything below about states, anchors,
ringing and scheduling stands; the two places the reversal touches carry a note.

`AGENTS.md` §1 defines UpYet as an alarm clock, and `GLOSSARY.md` already draws the line: a timer rings like
an alarm but never records evidence and never produces an **Occurrence**. Issue #10 asked what that means
concretely. This ADR answers with the vocabulary, the state machine, the ringing surface, the persistence
split and the one exact alarm a timer needs. It changes nothing on the alarm path.

**Five states, named after what the user sees.** A timer is `Idle`, `Running`, `Paused`, `Ringing` or
`Missed`, mapping onto AOSP DeskClock's `RESET`, `RUNNING`, `PAUSED`, `EXPIRED` and `MISSED`. We keep our
own names because `GLOSSARY.md` reserves *Ringing* for "the alarm is sounding", and *expired* describes an
error rather than a timer doing its job.

**Two anchors, because `elapsedRealtime` resets at boot.** A `Running` timer stores `remainingAtAnchor`
alongside both a wall-clock anchor and an elapsed anchor; remaining time is a pure function of either pair,
clamped at zero. A wall-clock anchor moves when the user changes the clock; an elapsed anchor resets at
reboot, so a countdown needs both.

| Event \ State | Idle | Running | Paused | Ringing | Missed |
|---|---|---|---|---|---|
| start / resume | → Running, full length | no-op | → Running, keeps remaining | no-op | no-op |
| pause | no-op | → Paused, remaining frozen | no-op | not offered | not offered |
| reset | no-op | → Idle, length restored | → Idle, length restored | → Idle, length restored, ringing stops | → Idle, length restored, notification cleared |
| +1 min | not offered | Running, remaining + 60 s | Paused, remaining + 60 s | → Running, 60 s, ringing stops | → Running, 60 s |
| expiry alarm fires | ignored as stale | → Ringing | ignored | ignored | ignored |
| stop | no-op | no-op | no-op | → Idle | no-op |
| delete | removed | removed | removed | removed, ringing stops | removed, notification cleared |
| boot or discovery | unchanged | → Running, → Missed or → Ringing | unchanged | → Ringing again | unchanged |
| wall-clock change | unchanged | remaining recomputed from the elapsed delta | unchanged | unchanged | unchanged |

**Ringing is its own service, and it is not the alarm's.** `TimerPlaybackService` (`mediaPlayback`
foreground service) and `TimerRingingActivity` mirror `AlarmPlaybackService` and `RingingActivity` but share
only `AlarmSoundPlayer` and `AlarmVibrator`. Reusing the alarm service would put timers inside the object
that owns occurrences, snooze budgets, evidence and the single ringing session, and alarm reliability
outranks any code that reuse would save. Timer ringing never records evidence, never creates an occurrence
and never appears in History. *(Amended by ADR 0010: with **Timer evidence** on, a timer ring does record
and does leave one occurrence per showing of the ringing screen. The reason for keeping the services apart
is unchanged.)*

**Several timers can ring at once, with one sound.** The ringing screen lists every ringing timer with
**Stop** and **+1 min**, and adds **Stop all** when two or more are ringing. One sound and one wake lock
serve all of them, and the notification's text is the count. The alarm path's single-slot
`RingingSessionRegistry` cannot express that, so timer ringing gets its own registry holding a set. Pause is
not offered while ringing: there is nothing to pause, and a minute more is the gesture a half-awake user
wants.

**One exact alarm, for whichever timer ends first.** `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)`
with one fixed request code, re-armed after every mutation, on boot, on unlock and on app launch. A delivery
is validated against the timer's current end instant and dropped as stale when it no longer matches, so a
cancelled, paused, reset or extended timer cannot ring an alarm that is no longer its own. Elapsed-realtime
alarms do not move when the user changes the clock, so `TIME_SET` and `TIMEZONE_CHANGED` re-arm nothing;
they only recompute remaining time from the elapsed delta and ignore a negative delta.

**Room is the truth; the mirror is what rings before first unlock.** Room owns the list, labels and editing.
A device-protected mirror, encoded like `DirectBootAlarmStore`, carries id, state, total length, remaining
at its anchor, wall-clock anchor, end instant, label and ringtone URI. Before first unlock the mirror is
authoritative and Room is untouched; `USER_UNLOCKED` reconciles mirror into Room. Timers are not history, so
a ring that only ever existed in the mirror loses nothing. *(Amended by ADR 0010: a timer ring is history
when its **Timer evidence** is on, so a ring that happened before first unlock persists
`DIRECT_BOOT_UNAVAILABLE` rather than vanishing; it still loses no video, because none could be recorded.)*

**The timer mirror carries the label, and the alarm mirror does not.** `AGENTS.md` §11 already permits a
label in the minimal mirror; `docs/ARCHITECTURE.md` said labels are not mirrored, which was only ever true
of alarms. Both statements now agree: alarm labels are not mirrored, a deliberate choice this ADR does not
reopen, and timer labels are, because before first unlock the ringing screen has no other way to tell one
ringing timer from another.

**Missed means silent, and more than a minute late.** Discovery happens at `LOCKED_BOOT_COMPLETED`,
`USER_UNLOCKED` and app launch. A `Running` timer that is more than 60 s overdue becomes `Missed`: it does
not ring, records nothing, and posts a low-importance "Timer missed" notification that Reset or delete
clears. A timer 0 to 60 s overdue is still inside its grace window and rings through an immediate exact
alarm rather than from `BootReceiver`, so the platform still authorises the foreground service start. A
timer still in the future becomes `Running` again with a fresh alarm at its recomputed end. Idle and Paused
timers are untouched by any of this.

**One list, banded by state.** Ringing, then Missed, then Running, then Paused, then Idle; remaining time
ascending inside Running and Paused; newest first inside Idle. Stopping a timer returns it to Idle at its
original length and it stays in the list, including one created by `SET_TIMER`; a timer tab that forgot
every timer after use would be worse than the behaviour it copies. `+1 min` on an Idle timer is not offered,
because starting from 60 s is a different action wearing the same name.

## Considered options

- **DeskClock state names (`RESET`, `EXPIRED`).** Rejected: familiar to anyone reading AOSP, but the words
  collide with this project's `Ringing` and read as failures.
- **Reuse `AlarmPlaybackService` and `RingingActivity`.** Rejected: one code path at the cost of putting
  timers next to occurrence recording, evidence and snooze bookkeeping, which is exactly what we promise
  not to risk.
- **One exact alarm per timer.** Rejected: a timer's end is a single global minimum, and per-timer identity
  is safe but multiplies into the exact-alarm quota for no benefit.
- **No label in the timer mirror.** Rejected: the ringing screen would show several anonymous ringings
  before first unlock.
- **Ring late on unlock when only a few minutes behind.** Rejected: a timer's job is done, and ringing it
  after the user has already unlocked is noise.
- **`+1 min` on Idle starts a 60 s timer.** Rejected: silently changes what the control means instead of
  offering a start button.
- **Delete a `SET_TIMER` timer once it fires.** Rejected: unlike a **Voice alarm** a timer has no occurrence
  to preserve, but it does have a list worth keeping. *(Amended by ADR 0010: a timer ring can now leave an
  occurrence with evidence attached, so deleting the timer after it fires would discard that too. The
  rejection stands, and its evidence argument now points the same way as its list argument.)*

## Consequences

`AGENTS.md` §6 used to say only `setAlarmClock()` schedules a real alarm occurrence, and §20 forbade
"timers" as a scheduling mechanism. The carve-out that lets a timer use `setExactAndAllowWhileIdle` is
`0009-timers-are-the-one-exact-alarm-that-isnt-setalarmclock.md`, and the `AGENTS.md` §6a and §20 wording
that admits it landed with issue #11. §20's forbidden "timers" now names in-process timers only
(`Handler`, `java.util.Timer`, coroutine `delay`), so the platform call a timer needs is no longer caught
by it.

None of this exists in code yet. Two claims are reasoned but unverified and need a device: that a timer's
full-screen intent still launches on Android 14+, where the permission is restricted to alarm and calling
apps, and that one wake lock covers several ringing timers without the foreground service being killed
mid-ring.
