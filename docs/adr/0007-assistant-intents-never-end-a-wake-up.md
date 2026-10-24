# Assistant intents never end a wake-up

UpYet handles the platform `AlarmClock` intents so assistants and other apps can create, show and dismiss
its alarms and timers, but it deliberately breaks the contract in two places, both to protect the evidence
of a **Wake-up**. Recorded here so neither gets "fixed" back to spec.

**`SNOOZE_ALARM` is not declared.** It only ever acts on the ringing alarm, so honouring it would let an
assistant snooze a wake-up without the ringing screen ever being used. Not declaring it is more honest
than declaring it and ignoring it.

**`DISMISS_ALARM` never dismisses a wake-up in progress.** If the matching alarm is ringing, UpYet brings
`RingingActivity` to the front (the device stays locked) and the alarm keeps ringing. If it is snoozed,
nothing is dismissed and a toast says it can be dismissed when it rings again. Only an alarm that is not
part of a wake-up is acted on: a recurring alarm gets **Skip next**, a one-time alarm is **Turned off**,
and nothing is deleted. When a search matches more than one alarm (label or "all" modes), UpYet acts on
none and opens the Alarms tab, because "dismiss all my alarms" silently skipping every wake-up is the
worst outcome a voice command can produce.

AOSP DeskClock does both silently from an invisible lock-screen activity; we chose not to follow it.
`com.android.alarm.permission.SET_ALARM` is a normal-level permission any app holds, so the platform gives
us no way to tell a trusted assistant from any other installed app.

The older premise, that a timer has no evidence to protect, is superseded for a recording-on timer (see the
2026-10-24 amendment below). Otherwise timer intents, including `DISMISS_TIMER`, are honoured as specified.

## Considered options

- **Follow the contract (DeskClock).** Rejected: an alarm that can be ended by any app or a half-awake voice
  command cannot answer "did I actually wake up?".
- **Declare `SNOOZE_ALARM` and open the ringing screen.** Rejected: the caller asked for a snooze and gets
  something else; not declaring it lets the assistant say plainly that UpYet cannot do it.

## Consequences

Assistant behaviour on the lock screen (for example a spoken "silence" arriving as `DISMISS_ALARM`) has
not been observed on a device yet. If it sends something this contract does not cover, this ADR is
revisited, not quietly bent.

## Amendment: 2026-10-24

ADR 0010 lets a timer record evidence when its **Timer evidence** is on, so `DISMISS_TIMER` can no longer
always end a ring silently. It now follows `DISMISS_ALARM`: when the intent matches a ringing timer whose
Timer evidence is on, UpYet brings `TimerRingingActivity` to the front (the device stays locked) and the
timer keeps ringing, so only the screen's Stop or **+1 min** ends it and the recording happens as it would
have without the intent. It is brought forward the same way `DISMISS_ALARM` brings `RingingActivity` forward,
through the ringing notification's full-screen intent or its content intent, and never by a background
`startActivity()`. A ringing timer with Timer evidence off stops directly, because there is no
evidence to lose, and a non-ringing timer is unaffected: it is still stopped or deleted as the contract
says. Everything else above stands unchanged, including the refusal to snooze without the ringing screen.

The rule is carried into `AGENTS.md` §10 so it is testable, and ADR 0010 cites this amendment. This makes a
third place where UpYet deliberately breaks the contract, after the two above, and for the same reason: to
protect the evidence.
