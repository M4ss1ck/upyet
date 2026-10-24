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

Timer intents are honoured as specified, including `DISMISS_TIMER`: a timer has no evidence to protect.

## Considered options

- **Follow the contract (DeskClock).** Rejected: an alarm that can be ended by any app or a half-awake voice
  command cannot answer "did I actually wake up?".
- **Declare `SNOOZE_ALARM` and open the ringing screen.** Rejected: the caller asked for a snooze and gets
  something else; not declaring it lets the assistant say plainly that UpYet cannot do it.

## Consequences

Assistant behaviour on the lock screen (for example a spoken "silence" arriving as `DISMISS_ALARM`) has
not been observed on a device yet. If it sends something this contract does not cover, this ADR is
revisited, not quietly bent.
