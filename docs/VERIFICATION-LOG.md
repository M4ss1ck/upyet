# UpYet — verification log

The record of what was actually run against real hardware, and what happened.

**Append-only.** Newest run first. An entry is never edited after the fact — not to correct a result, not
to tidy a note. A later run supersedes an earlier one by being newer, and the earlier entry stays exactly
as it was written. The only permitted edits to a past entry are typographical.

Read it with `docs/TESTING.md` open: the scenario slugs used here are defined in that file's matrix, and
the lanes (`automated` / `adb` / `human-eyes`) mean what §4 says they mean.

Rules for an entry:

* Name the hardware and the package. An emulator run is labelled emulator; a `physical` scenario run on
  an emulator is a regression result, never a device result.
* Every scenario attempted gets a row: `PASS`, `FAIL` or `SKIPPED`. A scenario that was not attempted is
  simply absent — but a scenario that was skipped on purpose is present, with the reason.
* Findings are written down whether or not they were fixed. A `FAIL` that turned out to be the test's
  fault is still a finding.

## Entry template

```markdown
## <ISO date> — <device model>, Android <ver> (API <n>), <debug|release> <upyet.version>

Lane: <automated | adb | human-eyes | mixed>   Build: <versionName> (<versionCode>)   Package: <applicationId>

| Scenario | Result | Notes |
|---|---|---|
| core-path | PASS | … |

### Findings
- …
```

## Runs

## 2026-08-26 — emulator (`sdk_gphone64_x86_64`), Android 14 (API 34), debug 0.1.5 — instrumented suite green

Lane: automated   Build: 0.1.5 (105)   Package: `dev.upyet.debug`

Re-run after fixing the three failures recorded in the entry below. **18 tests, 0 failures, 0 errors.**

| Scenario | Result | Notes |
|---|---|---|
| `core-path` | PASS | Unchanged from the previous run |

### Findings

- All three previously failing instrumented tests were diagnosed to root cause and fixed. None was an
  application bug; all three were defects in the tests or the build.
  - `AlarmListScreenTest` asserted a hardcoded `"7:30 AM"`. ICU 72, which shipped in Android 14, changed
    the space before the day period from U+0020 to U+202F NARROW NO-BREAK SPACE. Confirmed on-device: the
    formatter emits `U+0037 U+003A U+0033 U+0030 U+202F U+0041 U+004D`. The app was right; the literal was
    wrong, and would have been wrong again on any other locale. The test now formats its expectation.
  - `NavigationTest` drove a `NavHostController` from the instrumentation thread. Setting `graph`
    registers a lifecycle observer, which `LifecycleRegistry` requires on the main thread. Now wrapped in
    `runOnMainSync`, which is also how the app uses a NavController.
  - `MigrationSmokeTest` hit `AbstractMethodError` from kotlinx-serialization. `room-migration:2.8.4`
    needs serialization 1.8.1 to parse the exported schema JSON; `androidx.savedstate:1.4.0`, pulled
    through lifecycle and navigation, requires 1.7.3 *strictly*, and that strict constraint silently
    downgraded Room's requirement. Fixed by forcing serialization to 1.8.1 on the androidTest classpaths
    only. The application's own classpaths still resolve 1.7.3 with no conflict and are unchanged.
- Lint's deliberate-warning count moved from 14 to 18: the four new `NewerVersionAvailable` entries are
  the serialization pins above. `AGENTS.md` records the new count and why.

## 2026-08-26 — emulator (`sdk_gphone64_x86_64`), Android 14 (API 34), debug 0.1.5

Lane: automated   Build: 0.1.5 (105)   Package: `dev.upyet.debug`

Emulator run. Not a physical-device result: `core-path` is a `physical` scenario, so this is a regression
gate only. The AVD was started with `-camera-front emulated`; with the AVD default of
`hw.camera.front=none` no evidence assertion is exercisable at all.

| Scenario | Result | Notes |
|---|---|---|
| `core-path` | PASS | `CorePathFlowTest`, on the emulator. Alarm fired, screen woke, `RingingActivity` took the focused window, CameraX reported Start, ≥3 s recorded, dismiss worked, segment finalized `RECORDED` with a non-empty file. The keyguard claim was **not** exercised — no secure lock screen on this AVD, which the test logs rather than passing over |
| everything else | SKIPPED | Not attempted in this pass. The `emulator-ok` adb-lane scenarios (`doze`, `clock-change`, `timezone-change`, `low-storage`, `retention-cleanup`) remain unrun |

### Findings

- **Three instrumented tests fail on this emulator, and they are not new.** `AlarmListScreenTest.rendersAlarmTimeAndLabel`
  (expects `7:30 AM`), `MigrationSmokeTest.opensVersionOne` (`AbstractMethodError` from
  kotlinx-serialization), `NavigationTest.destinationsAreReachable` (`addObserver must be called on the
  main thread`). Confirmed pre-existing by running them at `3f23ad9`, which predates both the history
  thumbnail work and this harness: identical failures, same three tests. Suite total 18 tests, 3 failures.
  Cause not yet diagnosed; none of the three touches the alarm path.
- **The documented `am broadcast` scheduling recipe was dead.** `DebugAlarmReceiver` was
  `exported="false"`, so broadcasts from the shell uid were silently dropped — the command reported
  `result=0` and no alarm was ever scheduled. It is now exported under the `DUMP` permission, which only
  shell and system hold. The receiver exists solely in the debug source set.
- **`DebugAlarmReceiver` hardcoded `evidenceEnabled = false`,** so an alarm scheduled the documented way
  could never record evidence. Now an `--ez evidence` extra, defaulting true.
- **The AVD had no front camera.** The app binds `DEFAULT_FRONT_CAMERA` only, so the first run failed with
  `CAMERA_UNAVAILABLE`. Now a documented prerequisite.

