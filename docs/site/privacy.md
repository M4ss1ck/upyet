---
title: UpYet privacy policy
permalink: /privacy/
---

# UpYet privacy policy

_Last updated: 2026-10-27._

UpYet is an alarm clock that records short video of you dismissing your alarm, so you can answer
"did I actually wake up?" later. It also replaces your phone's Clock app, with a world clock, timers and
a stopwatch. Everything it records stays on your phone.

## The short version

UpYet has no account, no server, and no network permission. It cannot send your data anywhere,
because it cannot reach the network at all.

## What UpYet records, and where it goes

**Video.** While UpYet's own alarm screen is on your display, the front camera records silent video.
Timers can record too, but only when you turn it on: a timer whose **Timer evidence** switch is on records
the same way while UpYet's own timer screen is on your display. The switch in Settings is off by
default, and each new timer starts with whatever that switch says. Recording happens only while one of
those two screens is showing: never in the background and never while another app is in front. The world
clock and the stopwatch never use the camera. No audio is ever recorded: UpYet does not request
microphone access and could not record sound if it tried.

Those videos are written to private storage that belongs to UpYet alone. Other apps cannot read them.
They are excluded from Android's cloud backup, so they are not copied to Google Drive or anywhere else.
By default they are deleted automatically after seven days; you can change that to one day, thirty days,
or keep them indefinitely, in Settings.

**Alarms, timers, clocks and settings.** Your alarm times, labels and chosen ringtones, your timers and
their labels, the list of places you picked for the world clock, the stopwatch's running time and laps,
and your app preferences are stored in UpYet's own private storage on the device. They are not backed up
to the cloud and are not readable by other apps. The world clock never reads your location: you pick
places from a list of cities that ships inside the app, so choosing one sends nothing anywhere.

**Diagnostic log.** UpYet keeps a small local record of what its alarms and timers did — that an alarm
was scheduled, that it fired, that it was dismissed, that a timer rang, that the camera failed to start.
It contains times, event names and error codes. It deliberately contains no video, no file names of your
videos, and no alarm or timer labels. It is capped in size and older entries are discarded. Nothing sends it anywhere; it exists
so that *you* can send it, if you choose to (see below).

## What leaves your phone

Nothing, unless you send it yourself.

UpYet has two share buttons, and both open Android's own share sheet, where you pick the app that
receives the file:

- **Share evidence.** Sends copies of the clips from one wake-up, or from one timer occurrence that
  recorded.
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
| Camera | To record the video evidence while the alarm screen is visible, or the timer screen of a timer whose Timer evidence you turned on. Nothing else. |
| Notifications | To post the notification that takes over your lock screen when an alarm or timer rings, and to show a running timer or stopwatch. |
| Alarms & reminders | To ring alarms and timers at an exact time, which a clock must do. |
| Full-screen intent | To show the alarm or timer screen over the lock screen when it rings. |
| Vibrate | To vibrate when an alarm or timer rings. |
| Run at startup | To restore your scheduled alarms and running timers after the phone reboots. |

UpYet does **not** declare internet access.

## Deleting your data

Deleting a wake-up or a timer occurrence in UpYet's history deletes its video files. Deleting an alarm or
a timer keeps its past recordings in your history until you delete them there or the automatic deletion
removes them. Uninstalling UpYet removes everything it stored: videos, alarms, timers, world clock places,
the stopwatch, settings and the diagnostic log. There is nothing held elsewhere for us to delete,
because there is no elsewhere.

## Data sources

The world clock's city list, time zones and map outline are bundled inside the app; nothing is downloaded
while you use it. They come from:

- **Place data © GeoNames**, <https://www.geonames.org/>, licensed under the
  [Creative Commons Attribution 4.0 International License (CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/).
  GeoNames supplies each place's time zone and, where Natural Earth has none, its GeoNames id. Changes
  made: records were matched to Natural Earth places by id, or by name, country and distance; zones were
  canonicalised through IANA backward links and validated against IANA zone.tab; no other GeoNames field
  was kept.
- **Made with Natural Earth.** Free vector and raster map data @ naturalearthdata.com. Place names,
  coordinates and population (ne_10m_populated_places) and the world map's land outline (ne_110m_land),
  in the [public domain](https://www.naturalearthdata.com/about/terms-of-use/).
- **IANA Time Zone Database**, <https://www.iana.org/time-zones>. Time zone rules (zone.tab and backward),
  in the public domain.

The same attribution is in the app under Settings → About → Data sources and in the `NOTICE` file in the source.

## Children

UpYet is not directed at children and collects nothing from anyone.

## Changes to this policy

If this policy changes, the date at the top changes with it, and the history of every change is public in
the project's Git repository.

## Contact

UpYet is free software under the GNU General Public License v3 or later. Source, issues and contact:
<https://github.com/M4ss1ck/upyet>.
