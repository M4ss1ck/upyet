# Store submission

Everything Google Play asks for that is not the APK itself. The privacy policy is not here — it is a
published web page (`docs/site/privacy.md`, served at <https://m4ss1ck.github.io/upyet/privacy/>),
because Play requires a URL. How to release is in [`RELEASING.md`](RELEASING.md).

## Privacy policy URL

```text
https://m4ss1ck.github.io/upyet/privacy/
```

Paste that into **Play Console → App content → Privacy policy** and into the store listing.

## Data Safety declaration

**UpYet declares that it collects no data and shares no data.**

That is the honest answer, and the reasoning matters enough to write down, because "an app that records
video of your face" sounds like it must be declaring something.

Play defines *collection* as transmitting user data off the device, and *sharing* as transferring it to a
third party. UpYet does neither. It declares no `INTERNET` permission, so it is not merely policy that
stops it — the app has no capability to transmit anything. Video, alarms, timers, the world clock's place
list, the stopwatch, settings and the diagnostic log are written to app-private storage, excluded from cloud backup (`allowBackup=false`), and readable by no
other app.

Play's own guidance excludes from disclosure any data transferred solely by an explicit user action to an
app the user chooses. Both of UpYet's share actions — sharing a wake-up's clips and sharing a diagnostic
report — are exactly that: a button the user presses, followed by Android's own share sheet, in which the
user picks the receiving app. Neither is disclosable, and neither happens without a deliberate tap.

### Why not over-declare

Declaring video as "collected but not shared" would be a safer-looking lie. It is a lie because nothing is
collected, and it is not safer: the Data Safety section appears on the public store listing, so
over-declaring tells every visitor that a local-only clock app collects video of them. That is the worst
sentence this product's store page could contain, and it is false. Declare accurately.

### If a reviewer challenges it

The whole answer is one line in the manifest: there is no `INTERNET` permission. Point at
`app/src/main/AndroidManifest.xml`, at `allowBackup=false`, and at this document. The source is public.

### Console answers

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | n/a — nothing is transmitted |
| Do you provide a way for users to request that their data is deleted? | **Yes** — delete a wake-up or a timer occurrence in-app, or uninstall |

## Sensitive-permission justifications

Play requires a written justification for each of these in the Console, and all three are the kind of
declaration a reviewer reads closely. Each one names the timer case explicitly, because timers ring through
the same permissions as alarms and a justification that only mentions alarms would be false.

### Camera

> UpYet is a clock app (alarms, timers, world clock, stopwatch) whose core feature is video evidence that
> the user woke up and dismissed the alarm. The front camera records silent video **only** while one of
> UpYet's own full-screen ringing screens is visible and resumed: the alarm ringing screen, or the timer
> ringing screen when the user has turned that timer's Timer evidence on (off unless the user turns it on). Recording
> is bound to that Activity's lifecycle and stops the moment the screen is no longer visible. The world
> clock and the stopwatch never use the camera. There is no background camera use, no camera foreground
> service, and no audio: UpYet neither declares nor requests `RECORD_AUDIO`. The recording is written to
> app-private, non-backed-up storage and never transmitted; the app declares no `INTERNET` permission. A
> camera failure never blocks dismissing an alarm or stopping a timer.

**Prominent disclosure.** Play requires an in-app disclosure shown before camera access begins, describing
the data and its use, with affirmative consent that is not merely the system permission dialog. UpYet's is
`PermissionOnboardingCard` on the alarm list — shown before the camera permission is ever requested, and
therefore before any recording is possible. It is not dismissible-and-forgotten: it remains until the
permissions are granted. The user's affirmative action is its button, which is what triggers the system
dialog. Turning Timer evidence on, in the Settings Timers section or on a timer, shows the same card first
while the camera permission is not granted, so the system camera dialog is never the first thing a user
sees from either entry point. See `docs/adr/0006` and its 2026-10-24 amendment.

The card is headed **"Before the camera turns on"** and reads, verbatim:

> UpYet records silent video from the front camera — no sound, ever — and only while UpYet's own alarm
> screen is on your display. Timers can record too, if you turn it on. It never records in the background.
> Clips are saved on this phone alone, are never sent anywhere, and are deleted automatically after the time
> you choose in Settings, where you can also turn recording off. Notifications let the alarm take over your
> lock screen.

The button beneath it reads **"Agree & continue"**. This is `permission_onboarding_explanation` in
`app/src/main/res/values/strings.xml`; if the string changes, change it here too.

### USE_FULL_SCREEN_INTENT

> UpYet replaces the phone's Clock app — its primary, user-facing purpose is to ring alarms and timers at
> the times the user sets. The full-screen intent is used for exactly two notifications, both of them a
> ringing clock: the alarm ringing screen when an alarm fires, and the timer ringing screen when a timer
> the user started reaches zero. That is the defining behaviour of an alarm clock and a timer, and the use
> Android documents this permission for. It is never used for advertising, promotions, or any notification
> that is not a currently-ringing alarm or timer: the upcoming-alarm, running-timer, missed-timer and
> stopwatch notifications carry no full-screen intent. UpYet does not bypass the keyguard: after the user
> dismisses an alarm or stops a timer, the device remains locked.

### USE_EXACT_ALARM

> UpYet's primary function is an alarm clock, and it replaces the phone's Clock app, timers included. Alarms
> must fire at the exact wall-clock time the user set, and a timer must ring the moment its countdown
> reaches zero; an inexact alarm would make either feature not work. UpYet schedules exactly two kinds of
> exact alarm, both user-created: each alarm occurrence, with `AlarmManager.setAlarmClock()`, which
> surfaces the next alarm to system UI; and one alarm for the earliest-ending running timer, with
> `AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)`, because a countdown is measured in
> elapsed time and is not the device's next alarm clock, so it must not be shown as one. No other exact
> alarm exists.

## Content rating questionnaire

| Question | Answer |
| --- | --- |
| Category | Utility / Productivity / Communication / Other |
| Violence, sexuality, profanity, controlled substances, gambling | None |
| Does the app share the user's location? | No |
| Does the app allow users to interact or exchange content? | No |
| Does the app allow users to purchase digital goods? | No |
| Does the app contain ads? | No |
| Does the app collect personal information? | No |

Expected outcome: **Everyone / PEGI 3**.

## Store listing copy

### App name (30 characters max)

```text
UpYet: Alarm With Proof
```

### Short description (80 characters max)

```text
Alarm clock that records proof you got up. Everything stays on your phone.
```

(74 characters. Play's limit is 80; recount with a script whenever this line changes.)

### Full description (4000 characters max)

```text
Did you actually wake up, or did you dismiss the alarm in your sleep and never remember it?

UpYet is a replacement clock app that answers that question. When an alarm rings, UpYet takes over
your lock screen with its own alarm screen — and while that screen is showing, the front camera records
a short, silent video. Later, you open the app and watch yourself turning off the alarm. Or watch
yourself not doing it.

It replaces the rest of your Clock app too: a world clock, timers and a stopwatch, in the same app.

EVERYTHING STAYS ON YOUR PHONE

UpYet has no account, no server, and no internet permission at all. It is not that we promise not to
upload your videos — the app cannot reach the network. Your clips live in private storage that no other
app can read, are excluded from cloud backup, and are deleted automatically after seven days unless you
choose otherwise. No analytics, no crash reporting service, no ads, no tracking.

A REAL ALARM CLOCK FIRST

Evidence is the point, but a camera that fails must never cost you a morning. UpYet is built the other way
round from most apps like it: the alarm is completely independent of the camera, the database and the user
interface. If recording fails, the alarm still rings and dismissing it still works.

• Exact alarms that survive a reboot, Doze, and the app being killed
• Weekly recurrence with proper local-time handling across daylight saving changes
• Snooze, with a limit you set, and a rolled-up count so you can see you snoozed six times
• Auto-snooze instead of silently giving up on an alarm you slept through
• Skip the next occurrence without disabling a recurring alarm
• A heads-up notification before the alarm, so you can call off a wake-up you no longer need
• Gradual volume ramp, and a warning when your alarm volume is turned down to nothing
• Vibrate-only and fully silent alarms, for shared rooms and for deaf and hard-of-hearing users

THE REST OF YOUR CLOCK

• World clock: the places you pick, on a day-and-night world map, searchable from a list of cities bundled
  in the app, so nothing is downloaded and your location is never read
• Timers: as many as you need, running side by side, counting through a reboot and ringing over the
  lock screen, with Stop, +1 min and Stop all
• Stopwatch with laps, which keeps counting if the app is closed

Timers can record too, when you turn it on: switch on Timer evidence for one timer, or for every new
timer in Settings, and the timer's ringing screen records the same silent clip. Out of the box it is off.
The world clock and the stopwatch never use the camera.

THE HISTORY

Every alarm becomes a wake-up in your history: when it rang, how many times you snoozed, when you finally
dismissed it, and the video. A timer that recorded appears in the same list with a Timer badge. A stats card answers "how am I doing" — how many wake-ups you got on the
first ring, how many minutes you lost to snoozing, how many you missed entirely — over the last week, the
last month, or all time.

You can share a wake-up's clips through Android's share sheet if you want to show someone. That is the
only way anything leaves the app, it takes a deliberate tap, and you pick who receives it.

FREE SOFTWARE

UpYet is free software under the GNU General Public License v3. The complete source code is public, which
means the claims on this page are checkable rather than promises: https://github.com/M4ss1ck/upyet

PERMISSIONS

Camera, to record the evidence while an alarm screen, or a timer screen with Timer evidence on, is
visible — never in the background. Notifications and full-screen intent, to show a ringing alarm or timer
over your lock screen. Alarms & reminders, because alarms and timers must fire at an exact time. Vibrate,
and run-at-startup to restore your alarms and timers after a reboot.
No microphone. No location. No files and media. No internet.
```

## Graphics — shot list

Play requires a 512×512 app icon, a 1024×500 feature graphic, and at least two phone screenshots
(16:9 or 9:16, min 320 px, max 3840 px). These are **not** in the repository: they need a real device
with real alarms and a real lock screen. Capture them in one session against a release build, in this
order.

| # | Shot | What must be in frame | Caption |
| --- | --- | --- | --- |
| 1 | Alarm list | Three or four plausible alarms, at least one recurring weekday set and one silent alarm showing the muted icon. No "Two permissions to go" card — grant them first. | Alarms that actually go off |
| 2 | Ringing screen | The full-screen alarm over the lock screen, recording indicator and elapsed timer visible, Snooze and Dismiss both in frame. Use a staged face or none. | It records while it rings |
| 3 | History list | At least five wake-ups with real thumbnails, one showing a snooze rollup ("snoozed 3 times"), one showing a missed wake-up. | Proof you got up |
| 4 | Stats card | The stats card with a non-trivial window — first-try count, minutes lost, missed — over 30 days. | How the week actually went |
| 5 | Occurrence detail | A wake-up's clip mid-playback with its segment cards below. | Watch the moment back |
| 6 | Settings | Retention, evidence-by-default and the reliability card all visible, showing the local-only subtitle. | Everything stays on the phone |

Feature graphic: the wordmark on a dark background with the tagline "Proof you actually woke up".
No screenshots inside it, no claims that are not on this page.

Do not stage a shot that shows behaviour the app does not have.
