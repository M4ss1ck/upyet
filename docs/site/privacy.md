---
title: UpYet privacy policy
permalink: /privacy/
---

# UpYet privacy policy

_Last updated: 2026-08-27._

UpYet is an alarm clock that records short video of you dismissing your alarm, so you can answer
"did I actually wake up?" later. Everything it records stays on your phone.

## The short version

UpYet has no account, no server, and no network permission. It cannot send your data anywhere,
because it cannot reach the network at all.

## What UpYet records, and where it goes

**Video.** While UpYet's own alarm screen is on your display, the front camera records silent video.
Recording happens only then — never in the background, never while another app is in front, and never
while the alarm screen is not visible. No audio is ever recorded: UpYet does not request microphone
access and could not record sound if it tried.

Those videos are written to private storage that belongs to UpYet alone. Other apps cannot read them.
They are excluded from Android's cloud backup, so they are not copied to Google Drive or anywhere else.
By default they are deleted automatically after seven days; you can change that to one day, thirty days,
or keep them indefinitely, in Settings.

**Alarms and settings.** Your alarm times, labels, chosen ringtones and app preferences are stored in
UpYet's own private database on the device. They are not backed up to the cloud and are not readable by
other apps.

**Diagnostic log.** UpYet keeps a small local record of what its alarms did — that an alarm was
scheduled, that it fired, that it was dismissed, that the camera failed to start. It contains times,
event names and error codes. It deliberately contains no video, no file names of your videos, and no
alarm labels. It is capped in size and older entries are discarded. Nothing sends it anywhere; it exists
so that *you* can send it, if you choose to (see below).

## What leaves your phone

Nothing, unless you send it yourself.

UpYet has two share buttons, and both open Android's own share sheet, where you pick the app that
receives the file:

- **Share a wake-up.** Sends copies of the clips from one wake-up.
- **Share a diagnostic report.** Sends the diagnostic log described above, together with your app
  version, Android version, phone model and current UpYet settings, so a bug report contains enough to
  act on.

Once you send something through the share sheet, it is out of UpYet's hands. UpYet's automatic deletion
will not reclaim that copy, and deleting the original inside UpYet will not delete it either. What the
receiving app does with it is between you and that app.

## What UpYet does not do

- No account, sign-in, or profile.
- No servers, no cloud sync, no backend of any kind.
- No analytics, no telemetry, no crash reporting service, no advertising, no advertising identifier.
- No tracking, and no sharing or selling of anything to anyone.
- No location, contacts, microphone, or files-and-media access — UpYet does not request these permissions.
- No background camera use of any kind.

## Permissions UpYet asks for, and why

| Permission | Why |
| --- | --- |
| Camera | To record the video evidence while the alarm screen is visible. Nothing else. |
| Notifications | To post the alarm notification that takes over your lock screen when an alarm rings. |
| Alarms & reminders | To schedule alarms at an exact time, which an alarm clock must do. |
| Full-screen intent | To show the alarm screen over the lock screen when an alarm rings. |
| Vibrate | To vibrate when an alarm rings. |
| Run at startup | To restore your scheduled alarms after the phone reboots. |

UpYet does **not** declare internet access.

## Deleting your data

Deleting a wake-up in UpYet's history deletes its video files. Uninstalling UpYet removes everything it
stored: videos, alarms, settings and the diagnostic log. There is nothing held elsewhere for us to delete,
because there is no elsewhere.

## Children

UpYet is not directed at children and collects nothing from anyone.

## Changes to this policy

If this policy changes, the date at the top changes with it, and the history of every change is public in
the project's Git repository.

## Contact

UpYet is free software under the GNU General Public License v3 or later. Source, issues and contact:
<https://github.com/M4ss1ck/upyet>.
