# The log redaction rule does not depend on build type

`AGENTS.md` §12 reads: "Logs never contain video content, file paths of evidence, or user labels **in
release builds**." That qualifier is reasonable for logcat — a debug build's logcat is read by a developer
at a desk, and an alarm label in it is a convenience, not a leak.

The persisted diagnostic log (`docs/adr/0004`) is a different object. It is a file, it survives, and it
exists to be shared out of the app through the system share sheet. The build type that produced it does
not change who ends up reading it.

We decided the persisted log applies the redaction rule unconditionally: no alarm labels, no evidence
file names, no evidence paths, in any build. `AlarmLog` enforces it at the point of writing rather than
at the point of sharing, so there is no window in which a redactable value exists in the file.

The rejected alternative was to keep the §12 distinction and redact on export. It fails in the direction
that matters: the file on disk would contain labels, and any future code path that reads or copies it
would leak them without anyone having decided that.

## Consequences

Alarm ids appear in the log; alarm labels never do, so a report says `alarmId=7` and the developer
reading it cannot tell that alarm was called "Chemotherapy". That is the intended trade: an id is enough
to follow one alarm through a log, and it is the label that is sensitive.

Scheduled trigger times *are* logged. A wall-clock time is what the log exists to explain and is not
covered by the §12 list.

`AGENTS.md` §12 keeps its "in release builds" wording, because it is still the right rule for logcat.
This ADR is the reason the persisted log is stricter than the line it descends from.
