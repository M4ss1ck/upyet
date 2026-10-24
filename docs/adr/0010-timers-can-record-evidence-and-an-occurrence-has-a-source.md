# Timers can record evidence, and an occurrence has a source

`0008-a-timer-is-five-states-and-one-exact-alarm.md` said a timer never records evidence and never
produces an **Occurrence**. Issue #11 reversed the first half and generalised the second: a timer the user
asked to record does record, and a ringing that records is an occurrence, whether an alarm or a timer made
it. This ADR records the reversal, the switches that gate it, and the shape the data has to take.

**Two switches, and off is the default.** A global "Record when a timer rings" in a new Timers section of
Settings, off by default, and a per-timer **Timer evidence** toggle that takes the global value at
creation. The global switch is a default, not a control: flipping it never changes a timer that already
exists. A timer created by a voice `SET_TIMER` takes the global value, because there is nobody there to
ask, which is precisely why the global switch exists.

**Editable until it rings.** Timer evidence can be changed while the timer is `Idle`, `Running` or
`Paused`, and not while it is `Ringing`, where the screen offers Stop, **Stop all** and **+1 min** and
nothing else. The decision belongs to before the ring, not during it.

**A remote intent cannot end a recording ring.** `DISMISS_TIMER` behaves like `DISMISS_ALARM`: when it
matches a ringing timer with Timer evidence on, `TimerRingingActivity` is brought forward, the same way
`DISMISS_ALARM` brings `RingingActivity` forward, through the ringing notification's full-screen intent or its
content intent and never a background `startActivity()`, and the timer keeps ringing, so the recording still
happens. A recording-off ringing timer stops directly, and a non-ringing timer is unaffected (ADR 0007,
amendment of 2026-10-24).

**An occurrence has a source: alarm or timer.** An occurrence stays what it was, one ringing and the thing
History records, and now carries whether it came from an alarm or a timer. An alarm ring always leaves one.
A timer ring leaves one only when it records, so History never fills with entries the user did not ask for.
Alarm occurrences keep everything: snooze chains, **Wake-ups**, stats. Timer occurrences never snooze and
never form a wake-up, so the wake-up arithmetic at the centre of the product is untouched. History shows
both sources in one list, timer entries carry a "Timer" badge, and there is deliberately no extra filter:
evidence of a timer belongs beside evidence of an alarm, told apart the way a reader can see, not behind a
toggle.

**One showing of the timer ringing screen is one timer occurrence.** Several timers ring together on one
screen, so the screen, not the timer, is the unit: one timer occurrence per showing and one set of evidence
segments. A timer that starts ringing while the screen is already up joins that showing and that
occurrence rather than opening a second one. The occurrence lists only the timers that had **Timer
evidence** on.

**With mixed toggles, recording ends with the last recording timer.** Recording runs while at least one
recording-on timer is ringing and stops when the last of them is stopped or given **+1 min**, even if other
timers still ring. A +1 min takes that timer out of the showing exactly like Stop. The occurrence, unlike the
recording, spans the showing: it closes when the showing ends and never while a recording-on timer is still
ringing, so a timer that rings again while the showing continues rejoins the same occurrence, and a ring after
the showing has ended is a new showing and a new occurrence. A showing in which every ringing timer has
recording off produces no occurrence and nothing in History, unless a recording-on timer joins it while it is
still up. Missed timers never record, because they never ring.

**Retention, deletion and failure behave exactly as they do for alarms.** Deleting a timer occurrence
deletes its files; deleting a timer keeps its past evidence. Before first unlock a recording-on ring
persists `DIRECT_BOOT_UNAVAILABLE` and still rings: the device-protected timer mirror carries the per-timer
**Timer evidence** flag, because nothing else can tell the pre-unlock ringing screen which timers were
recording-on, and the pending occurrence goes through the alarm path's `PendingOccurrenceStore` carrying the
timer attribution. That record also carries an explicit alarm / timer source discriminator, because the store
and its `USER_UNLOCKED` reconciler serve both paths: reconciliation dispatches on the source and never applies
an alarm-only effect, such as retiring a one-time alarm, to a timer record (ADR 0008). A record written
before the discriminator existed decodes as an alarm record, so the upgrade never drops an in-flight alarm,
and a timer occurrence ended from the ringing screen gets its own `STOPPED` outcome. Stop and **+1 min** work
regardless of camera state, under the same "evidence never blocks dismissal" rule as the alarm path.

**No new permission, and one more sentence of disclosure.** Turning timer evidence on uses the camera
permission the app already asks for, or requests it at that moment; `RECORD_AUDIO` stays unrequested. The
prominent disclosure of ADR 0006 must gain one sentence, "Timers can record too, if you turn it on", because
a disclosure that describes only alarms would no longer be describing what the camera is for. That card must
also gate the new entry point: turning Timer evidence on, in Settings or on a timer, shows it first while the
camera permission is not granted, so the system camera dialog is never the user's first sight of the request.
The gate is the permission the card already keys on, not a separate stored consent flag.

**The source and the timer attribution are one non-destructive Room migration.** An occurrence's source is
a new column defaulted to alarm, so every row that already exists stays valid and readable with no data
rewritten. A timer occurrence also needs to say which timers it covered, so the same migration adds a
snapshot of their id and label as text, never a foreign key, because deleting a timer keeps its past
evidence. It ends with the field an alarm dismissal already uses, `dismissedAt`, which for a timer records
the stop or the +1 min that ended the showing, since the occurrence spans the showing and not just the
recording; the field means "the user ended this", not "dismissed".

## Considered options

- **A separate "Timer ring" concept, kept out of History.** Rejected: it makes the two sets of evidence
  live in two places for no user benefit. Evidence is the product; a timer's clip belongs in the same
  list, told apart by a badge.
- **One occurrence per ringing timer, sharing one set of files.** Rejected: it makes "deleting an
  occurrence deletes its files" ambiguous, since deleting one timer's occurrence would either break
  another's evidence or leave the files unaccounted for. One showing, one occurrence, one owner.
- **A global switch only.** Rejected: the user wanted per-timer control, and a global switch alone cannot
  express "record the long one, not the kitchen one".
- **A per-timer toggle only.** Rejected: a `SET_TIMER` timer arrives with no editor, so the global default
  is the only way it can ever record.
- **Recording by default.** Rejected: a timer is not a wake-up, and the camera running for every timer
  would make it run for a user who never asked for it.

## Consequences

This supersedes the "never records evidence, never creates an occurrence" parts of ADR 0008, not its
states, anchors, ringing surface or scheduling. ADR 0008 now carries a status line saying so.

`AGENTS.md` gains the invariants that make the rules enforceable: an occurrence has a source, the two
switches and their semantics, one occurrence per showing, and timer evidence obeying the alarm retention
and failure rules unchanged (issue #11). The two ringing screens share one evidence component, never two
copies, so a camera fix lands once.

The stopwatch and the world clock never use the camera: neither rings, so nothing in this ADR applies to
them.

None of this exists in code yet. The recorded, unverified claims are the same as ADR 0008's: a timer's
full-screen intent launching on Android 14+, and one wake lock surviving a multi-timer ring.
