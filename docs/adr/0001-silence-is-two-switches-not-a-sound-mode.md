# Silence is two independent switches, not a sound mode

`AGENTS.md` §15 prefers sealed interfaces for finite states, so the obvious model for "how does this
alarm ring" is a sealed `AlarmSound { Default | Custom(uri) | Silent }`. We chose instead to add a plain
`soundEnabled: Boolean` next to the existing `vibrationEnabled`, leaving `soundUri: String?` with its
current meaning (`null` = the system default alarm sound).

Two reasons. First, sound and vibration are genuinely independent — a vibrate-only alarm and a fully
silent alarm are both real configurations (see `CONTEXT.md`), so a sealed type covering only sound would
sit awkwardly beside a boolean covering vibration, and one covering both would enumerate four states that
two booleans already express. Second, `Silent` as a sealed case destroys the user's chosen ringtone:
switching sound off and back on would forget which sound they had picked. A boolean preserves it.

## Consequences

`soundEnabled = false` with a non-null `soundUri` is representable and meaningful — it is a remembered
choice, not an inconsistent state. Nothing may "clean it up" by nulling the URI when sound is switched off.
