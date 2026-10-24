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
`Paused`, and not while it is `Ringing`, where the screen offers Stop and **+1 min** and nothing else. The
decision belongs to before the ring, not during it.

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
recording-on timer is ringing and stops when the last of them is stopped, even if other timers still ring. A
showing in which every ringing timer has recording off produces no occurrence and nothing in History.
Missed timers never record, because they never ring.

**Retention, deletion and failure behave exactly as they do for alarms.** Deleting a timer occurrence
deletes its files; deleting a timer keeps its past evidence. Before first unlock a recording-on ring
persists `DIRECT_BOOT_UNAVAILABLE` and still rings. Stop and **+1 min** work regardless of camera state,
under the same "evidence never blocks dismissal" rule as the alarm path.

**No new permission, and one more sentence of disclosure.** Turning timer evidence on uses the camera
permission the app already asks for, or requests it at that moment; `RECORD_AUDIO` stays unrequested. The
prominent disclosure of ADR 0006 gains one sentence, "Timers can record too, if you turn it on", because a
disclosure that describes only alarms would no longer be describing what the camera is for.

**The source is a non-destructive Room migration.** An occurrence's source is a new column defaulted to
alarm, so every row that already exists stays valid and readable with no data rewritten. Nothing else in
the occurrence schema moves.

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
