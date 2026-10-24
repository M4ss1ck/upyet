# Research: the `AlarmClock` intent contract

Answers [#8](https://github.com/M4ss1ck/upyet/issues/8), part of map [#5](https://github.com/M4ss1ck/upyet/issues/5)
(UpYet as a full Clock replacement). Researched 2026-10-24 against minSdk 26 / targetSdk 36.

Research note, not a decision. Decisions that follow from it belong in an ADR.

## TL;DR

* Seven activity actions matter: `SET_ALARM`, `SET_TIMER`, `SHOW_ALARMS`, `SHOW_TIMERS`, `DISMISS_ALARM`,
  `SNOOZE_ALARM`, `DISMISS_TIMER`. All are **activity** intents: a handler is an exported `<activity>` with
  `category.DEFAULT` (and `category.VOICE` for assistant voice mode).
* Every extra is optional. With no time (`SET_ALARM`) or no length (`SET_TIMER`) the handler must open its
  setup UI and `EXTRA_SKIP_UI` is ignored.
* `com.android.alarm.permission.SET_ALARM` is a **`normal`** permission. Guarding a handler with it only
  stops apps that forgot to declare it; any installed app can get it at install time without a prompt.
* `DISMISS_ALARM` (search mode `NEXT` explicitly includes "the alarm that is currently ringing") and
  `SNOOZE_ALARM` (acts only on the currently ringing alarm) are exactly the "dismiss or snooze without the
  ringing experience" paths AGENTS.md section 7 forbids. AOSP DeskClock implements both by changing the
  ringing instance's state directly, from an invisible `Theme.NoDisplay` activity that is `showWhenLocked`.
* There is no Android role for "default clock app". Ordinary intent resolution picks the handler. Gemini
  prefers the OEM clock app (HONOR, OnePlus, OPPO, Samsung, TECNO, Xiaomi) or Google Clock when it is
  enabled, and otherwise supports only "set, check, update, and delete" in third-party clock apps.
* Play has no policy on `SET_ALARM` or the `AlarmClock` intents. The relevant policy is `USE_EXACT_ALARM`,
  which UpYet already qualifies for ("The app is an alarm or timer app").

**Recommendation:** handle `SET_ALARM`, `SHOW_ALARMS`, `SET_TIMER`, `SHOW_TIMERS`, `DISMISS_TIMER` fully.
Handle `DISMISS_ALARM` only for occurrences that are **not ringing** (skip next / turn off), and route a
ringing match to `RingingActivity`. Do **not** declare `SNOOZE_ALARM`. Details in
[Recommendation](#recommendation).

## Per-intent contract

String values from `AlarmClock.java`; API levels from the developer.android.com reference ("Added in API
level N"). Everything at or below API 26 is available on every UpYet device.

### Actions

| Action (`android.intent.action.*`) | API | Extras the handler reads | Platform semantics | AOSP DeskClock behaviour |
|---|---|---|---|---|
| `SET_ALARM` | 9 | `EXTRA_HOUR`, `EXTRA_MINUTES`, `EXTRA_DAYS`, `EXTRA_MESSAGE`, `EXTRA_RINGTONE`, `EXTRA_VIBRATE`, `EXTRA_SKIP_UI` | "Activates an existing alarm or creates a new one." No time of day: start an alarm-setting activity, `SKIP_UI` ignored. Time given + `SKIP_UI=true` + not repeating: remove the alarm after it is dismissed. An identical existing alarm "may" be reused (then not removed after dismissal). "This action always enables the alarm." In voice mode, report a deeplink to the alarm via `CompleteVoiceRequest` so follow-ups like `DISMISS_ALARM` can target it. | Rejects hour outside 0-23 or minutes outside 0-59 with a voice failure. No hour: opens the alarm list in "create new" mode. Looks up an alarm matching hour, minutes, days, plus label / vibrate / ringtone when those extras are present; if found, enables it and deletes its instances, else creates one with `deleteAfterUse = !repeating && skipUi`. Schedules the next instance, shows a toast, and unless `skipUi` opens the list scrolled to the alarm. Voice success prompt only, **no deeplink reported**. |
| `SET_TIMER` | 19 | `EXTRA_LENGTH`, `EXTRA_MESSAGE`, `EXTRA_SKIP_UI` | "Activates an existing timer or creates a new one." No length: open timer setup, `SKIP_UI` ignored. Length + `SKIP_UI=true`: remove the timer after it is dismissed. Identical unused timer may be reused. "This action always starts the timer." | No length: opens timer setup. Below `Timer.MIN_LENGTH`: voice failure. Reuses a reset timer with the same length and label, else adds one (`deleteAfterUse = skipUi`). Starts it; opens the Timers tab unless `skipUi`. |
| `SHOW_ALARMS` | 19 | none | "This action opens the alarms page." | Selects the Alarms tab, starts `DeskClock`. |
| `SHOW_TIMERS` | 26 | none | "This action opens the timers page." | Selects the Timers tab, scrolls to the newest timer. |
| `DISMISS_ALARM` | 23 | data URI (deeplink), `EXTRA_ALARM_SEARCH_MODE`, plus `EXTRA_HOUR`/`EXTRA_MINUTES`/`EXTRA_IS_PM` (mode `TIME`) or `EXTRA_MESSAGE` (mode `LABEL`) | Target by deeplink data URI, else by search mode. Neither: one active alarm is dismissed, several prompt the user. Search with 2+ matches: show a picker (and in voice mode a `PickOptionRequest`). One-time alarm: dismissing disables it. Repeating alarm: dismissing "only prevents the upcoming instance from ringing", the alarm stays enabled. | **Ignores the data URI.** Considers enabled alarms whose next instance is at most `FIRED_STATE` (so a ringing one is included). No mode + 2+ alarms: `AlarmSelectionActivity` picker. Otherwise filters by mode; mode `ALL` dismisses every match with no picker. Ringing or snoozed instance: `deleteInstanceAndUpdateParent` (the alarm stops, no UI). Upcoming within 24 h: `setPreDismissState`. More than 24 h away: refused. |
| `SNOOZE_ALARM` | 23 | `EXTRA_ALARM_SNOOZE_DURATION` (minutes, optional) | "Snoozes the currently ringing alarm." Duration applies to this ring only, does not change the default; absent, use a default such as 10 min. No ringing alarm: no-op. | Snoozes **every** instance in `FIRED_STATE` via `AlarmStateManager.setSnoozeState`, no UI. Ignores `EXTRA_ALARM_SNOOZE_DURATION`. No firing alarm: voice failure. |
| `DISMISS_TIMER` | 28 | data URI (deeplink to a timer) | With URI: dismiss that timer. Without: "dismiss all expired timers". | URI parsed with `ContentUris.parseId`; resets or deletes that timer. No URI: resets/deletes every expired timer. |

### Extras

| Extra | String value | API | Type, range, default |
|---|---|---|---|
| `EXTRA_HOUR` | `android.intent.extra.alarm.HOUR` | 9 | `Integer` 0-23. Absent on `SET_ALARM`: open setup UI. |
| `EXTRA_MINUTES` | `android.intent.extra.alarm.MINUTES` | 9 | `Integer` 0-59, default 0. |
| `EXTRA_MESSAGE` | `android.intent.extra.alarm.MESSAGE` | 9 | `String`. Label for `SET_ALARM`/`SET_TIMER`; search term for `ALARM_SEARCH_MODE_LABEL`. |
| `EXTRA_DAYS` | `android.intent.extra.alarm.DAYS` | 19 | `ArrayList<Integer>` of `java.util.Calendar` day constants (`SUNDAY`=1 ... `SATURDAY`=7). Absent: one-time alarm. DeskClock also accepts `int[]`. |
| `EXTRA_RINGTONE` | `android.intent.extra.alarm.RINGTONE` | 19 | `String`: `VALUE_RINGTONE_SILENT` (`"silent"`) or a content URI. Absent or URI missing: `content://settings/system/alarm_alert`. DeskClock treats `""` as silent too. |
| `EXTRA_VIBRATE` | `android.intent.extra.alarm.VIBRATE` | 19 | `Boolean`, default `true`. |
| `EXTRA_SKIP_UI` | `android.intent.extra.alarm.SKIP_UI` | 11 | `Boolean`, default `false`. `true`: "bypass any intermediate UI"; the common-intents guide says the app "must bypass any confirmation UI and set the specified alarm". Also marks a one-time alarm / timer as delete-after-use. |
| `EXTRA_LENGTH` | `android.intent.extra.alarm.LENGTH` | 19 | `Integer` seconds, 1-86400. Absent: open timer setup UI. |
| `EXTRA_ALARM_SEARCH_MODE` | `android.intent.extra.alarm.SEARCH_MODE` | 23 | `String`, one of the modes below. Used only when there is no deeplink data URI. |
| `EXTRA_IS_PM` | `android.intent.extra.alarm.IS_PM` | 23 | `Boolean` (false=AM, true=PM), only with mode `TIME`. Missing: hour is ambiguous, "ask for clarification". |
| `EXTRA_ALARM_SNOOZE_DURATION` | `android.intent.extra.alarm.SNOOZE_DURATION` | 23 | `Integer` minutes, `SNOOZE_ALARM` only. |
| `VALUE_RINGTONE_SILENT` | `silent` | 19 | Value for `EXTRA_RINGTONE`. |

Search modes (API 23): `ALARM_SEARCH_MODE_TIME` = `android.time` (closest match on hour/minutes/is-PM, at
least one required), `ALARM_SEARCH_MODE_NEXT` = `android.next` ("the alarm that will ring next, or the alarm
that is currently ringing, if any"), `ALARM_SEARCH_MODE_ALL` = `android.all`, `ALARM_SEARCH_MODE_LABEL` =
`android.label` (label contains the word or phrase).

### `EXTRA_SKIP_UI` in one paragraph

It means "do it, do not ask". It never applies when the request is incomplete (no time / no length). When
set on a complete `SET_ALARM`, the handler schedules without showing an editor or confirmation, and a
one-time alarm created this way is expected to remove itself after it is dismissed. DeskClock still shows a
toast and still reports to the voice interactor; only the activity hand-off is skipped.

## Permission

* Defined in `frameworks/base/core/res/AndroidManifest.xml`:
  `<permission android:name="com.android.alarm.permission.SET_ALARM" ... android:protectionLevel="normal" />`,
  described as "Allows an application to broadcast an Intent to set an alarm for the user."
* The `AlarmClock` class doc says handlers "should create an activity to handle the Intent that requires the
  permission". DeskClock does this with `android:permission="com.android.alarm.permission.SET_ALARM"` on both
  `HandleApiCalls` (DISMISS_ALARM, DISMISS_TIMER, SHOW_ALARMS, SHOW_TIMERS, SNOOZE_ALARM) and the
  `HandleSetAlarmApiCalls` alias (SET_ALARM, SET_TIMER).
* The common-intents guide tells *callers* to declare `<uses-permission ... SET_ALARM />` for `SET_ALARM` and
  `SET_TIMER`.
* Because the level is `normal`, the guard is not a security boundary. It is granted at install with no user
  prompt to any app that asks. Anything an exported handler does, any installed app can make it do.

## How a handler is chosen

* **Platform:** plain implicit-intent resolution. The `AlarmClock` class doc tells callers to use
  `startActivity()` "so that the user has the option of choosing which alarm clock application to use". With
  several handlers and no preferred one, the system resolver shows a chooser (with "Always"). There is no
  `RoleManager` role for a clock app. "Android TV devices may not support the alarm intents."
* **Gemini** ([Control your Android mobile device & apps with Utilities](https://support.google.com/gemini/answer/15235441?hl=en)):
  on HONOR, OnePlus, OPPO, Samsung, TECNO and Xiaomi devices "the Gemini mobile app uses the manufacturer's
  clock app". Elsewhere, "if the Clock app by Google is enabled on your device, the Gemini mobile app uses it
  over any other clock app." "For third-party clock apps, you can ask the Gemini mobile app to set, check,
  update, and delete alarms and timers. Other actions aren't supported." On the lock screen, with the
  setting on, Gemini can "set and silence alarms" (the page does not say whether that covers third-party
  apps).
* Consequence: on most devices UpYet only receives Gemini traffic if the user disables Google Clock (or the
  OEM clock, where that is possible). The page does not say which intents Gemini fires for "check" and
  "update", which have no public `AlarmClock` action. Unverified.
* **App Actions** (the successor to the old `developers.google.com/voice-actions/system` page, which now 301s
  to [App Actions overview](https://developer.android.com/guide/app-actions/overview)): live, last updated
  2026-07-16, no alarm or timer built-in intents mentioned. Not a route for clock handling.

## Play policy

* [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/13161072?hl=en):
  `USE_EXACT_ALARM` only "if their core functionality supports the need for an exact alarm"; acceptable use
  includes "The app is an alarm or timer app". UpYet already declares it (AGENTS.md section 6) and adding
  timers stays inside that use case.
* The same page does not mention `SET_ALARM` or `AlarmClock` (checked by text search of the fetched page).
  No Play policy restricts declaring or handling these intents. Handling them adds no Play declaration.

## Conflicts with AGENTS.md

| Contract | AGENTS.md rule it hits | Severity |
|---|---|---|
| `SNOOZE_ALARM`: snooze the currently ringing alarm, no-op otherwise. | Section 7: "it must not offer a Dismiss action that bypasses the recording experience. Snooze also happens in the Activity." Section 20: "Notification actions that dismiss the alarm without the ringing experience." | Hard conflict. The intent's only non-no-op meaning is forbidden. |
| `DISMISS_ALARM` with search mode `NEXT` ("or the alarm that is currently ringing"), mode `ALL`, mode `TIME`/`LABEL` matching a ringing alarm, or no extras with exactly one active alarm that is ringing. | Section 7 (as above); invariant 3 and the product definition (evidence of the user dismissing). | Hard conflict for a ringing occurrence. Any app holding the `normal` permission could end a wake-up with no evidence. |
| `DISMISS_ALARM` on a not-yet-ringing occurrence (repeating: skip the upcoming instance; one-time: disable). | None. Maps onto GLOSSARY **Skip next** and **Turn off**. Must not touch a ringing or snoozed occurrence. | Compatible with care. Note GLOSSARY says one-time alarms cannot be skipped: for them skip and turn off are the same act, which matches the platform text. |
| `SET_ALARM` + `SKIP_UI=true`: schedule without UI. | Invariant 10 / section 6: if scheduling fails (exact alarms unavailable), the UI must not claim the alarm is active, and no silent downgrade to inexact. | Compatible if failure is reported (voice `AbortVoiceRequest`, or open the app on the reliability path) rather than a success toast. |
| `SET_ALARM` + `SKIP_UI=true` one-time: "remove this alarm after it has been dismissed". | Invariant 9: deleting an alarm does not delete past evidence. | Compatible: remove the alarm, keep its occurrences and evidence. UpYet has no "delete after use" concept today; it needs a name in GLOSSARY. |
| "This action always enables the alarm" when reusing an identical alarm. | GLOSSARY **Turn off** discards skip and pending snooze. | Needs a decision: does re-enabling via intent also clear a pending **Skip next**? |
| DeskClock's handler is `directBootAware` and `showWhenLocked`. | Section 11: no Room/DataStore before `isUserUnlocked()`; section 7: never bypass the keyguard. | Avoidable: UpYet's handler need not be Direct Boot aware, and an editor opened from the lock screen must go through `requestDismissKeyguard` (user authenticates), never show over the keyguard. |
| `DISMISS_TIMER`, `SET_TIMER`, `SHOW_TIMERS`. | None. Map #5 already allows timer notification controls because a timer has no evidence experience. | Compatible. |

## Recommendation

1. **`SET_ALARM`**: declare and implement. No hour: open the editor for a new alarm. Hour present: validate
   ranges, reuse an identical alarm (match hour, minutes, days, and label/vibrate/ringtone when given) or
   create one, schedule through `AlarmScheduler`. `SKIP_UI=false`: open the alarm list scrolled to it.
   Scheduling failure is a failure, never a toast saying "set". Map `VALUE_RINGTONE_SILENT` + vibrate
   true to a **Vibrate-only alarm**, silent + vibrate false to a **Silent alarm**.
2. **`SHOW_ALARMS`, `SET_TIMER`, `SHOW_TIMERS`, `DISMISS_TIMER`**: declare and implement per the table.
   Timer actions land with the timer feature, not before.
3. **`DISMISS_ALARM`**: declare, but split by state. Not ringing: repeating alarm = **Skip next**, one-time
   alarm = **Turn off**, matching platform semantics and DeskClock's 24 h guard (refuse further out). Ringing
   or snoozed match: do not dismiss; bring `RingingActivity` to the front (it is `showWhenLocked`) and report a
   voice failure such as "Dismiss it on the ringing screen". Multiple matches: picker, never mode-`ALL` bulk
   dismissal without one.
4. **`SNOOZE_ALARM`**: do not declare. Its only effective case is forbidden, and declaring it would add
   UpYet to the chooser for an action it will always refuse. If a later decision wants it, the only
   compliant implementation is "surface `RingingActivity`", which is not a snooze.
5. Handler activity: exported, `android:permission="com.android.alarm.permission.SET_ALARM"` (convention,
   not protection), `category.DEFAULT` + `category.VOICE`, `Theme.NoDisplay`-style trampoline that finishes
   in `onCreate`, **not** `directBootAware`. In voice mode report via `CompleteVoiceRequest` /
   `AbortVoiceRequest`, and do better than DeskClock by including the alarm deeplink the platform asks for.
6. Record the ringing-state refusal for `DISMISS_ALARM` and the non-declaration of `SNOOZE_ALARM` in an ADR,
   since they are deliberate deviations from the platform contract made to keep AGENTS.md section 7.

## Open questions

* What intents does Gemini actually send to a third-party clock app for "check" and "update"? Settle by
  installing a test handler that logs every incoming intent and driving Gemini on a device with Google Clock
  disabled.
* Does Gemini's lock-screen "silence alarms" reach a third-party app, and with which action
  (`DISMISS_ALARM` or `SNOOZE_ALARM`)? Same experiment, alarm ringing, device locked.
* Is `deleteAfterUse` worth modelling (one-time alarms created by `SET_ALARM` + `SKIP_UI` vanish after
  dismissal), or does UpYet keep them as turned-off alarms? Product decision; evidence is kept either way.
* Should `SET_ALARM` reuse of an identical alarm clear a pending **Skip next**? Product decision.
* Does reading an `EXTRA_RINGTONE` content URI from another app need a URI grant UpYet will not have? Test
  with a caller that passes a non-system ringtone URI.
* `DISMISS_ALARM` deeplinks: UpYet would need a URI scheme for alarms if it reports deeplinks from
  `SET_ALARM`. DeskClock ignores the data URI entirely, so no reference implementation exists.

## Sources

Primary:

* `android.provider.AlarmClock` source, AOSP `frameworks/base` `main` (head `1cdfff5`):
  <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/AlarmClock.java>
* `AlarmClock` API reference (API levels): <https://developer.android.com/reference/android/provider/AlarmClock>
* Common intents, Alarm clock section: <https://developer.android.com/guide/components/intents-common#Clock>
* `SET_ALARM` permission definition, `frameworks/base/core/res/AndroidManifest.xml`:
  <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml>
* AOSP DeskClock `main` (head `04e481f`, 2025-02-27):
  * <https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/src/com/android/deskclock/HandleApiCalls.kt>
  * <https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/src/com/android/deskclock/FetchMatchingAlarmsAction.kt>
  * <https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/src/com/android/deskclock/controller/VoiceController.kt>
  * <https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/AndroidManifest.xml>
* Gemini Help, Control your Android mobile device & apps with Utilities:
  <https://support.google.com/gemini/answer/15235441?hl=en>
* Play Console Help, Permissions and APIs that Access Sensitive Information:
  <https://support.google.com/googleplay/android-developer/answer/13161072?hl=en>
* App Actions overview: <https://developer.android.com/guide/app-actions/overview>

Not used as evidence: third-party blog posts on choosing Assistant's clock app (could not be confirmed
against Google documentation).
