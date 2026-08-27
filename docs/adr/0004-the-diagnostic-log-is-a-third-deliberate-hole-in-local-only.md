# The diagnostic log is a third deliberate hole in local-only

`AGENTS.md` §12 says logs never contain video content, evidence file paths or user labels in release
builds, and §12 more broadly says the app has no crash SaaS and no network. Correct on both counts — and
the consequence is that a user whose alarm did not ring has nothing to send anybody. `AlarmLog` wrote to
logcat, which is gone by the time the user opens the app, and unreachable to them in any case.

We decided to persist alarm log events to a file and let the user share it. Three sub-decisions follow,
and each had a real alternative.

**Device-protected storage, not Room.** `AlarmLog.event()` is called from `AlarmReceiver` and
`AlarmPlaybackService`, both `directBootAware`. Putting the log in Room would mean a `isUserUnlocked()`
guard at every alarm-path call site and a Room dependency on the ringing path, which §8 forbids. A plain
append-only file in `createDeviceProtectedStorageContext().filesDir` needs neither, and it works before
first unlock — which is precisely the window where "the alarm never rang" lives.

**Size-based rotation, not age.** The obvious move is to tie the log's lifetime to the evidence retention
setting, seven days by default. That is backwards: the person filing "my alarm didn't ring last Tuesday"
is exactly the person whose clip retention already reclaimed. Two files of ~128 KB, current and previous,
bound the disk without discarding the report's subject.

**Synchronous writes.** A buffered writer on a background thread never touches the ringing path, and
loses the last lines before a process death — the lines a crash log exists to preserve. An ~80-byte
append is far cheaper than the wake lock, notification and `MediaPlayer` work already on that path.

## Consequences

The log is a file the user can send out of the app, so it is subject to `docs/adr/0005`: one redaction
rule, always, regardless of build type. It is also disclosed in the privacy policy and named in
`docs/STORE.md` as a user-initiated share, not collection.

Persisting on the ringing path means an I/O failure there must never propagate. Logging is best-effort:
a write that throws is swallowed, because failing to record that the alarm rang must never stop it
ringing.
