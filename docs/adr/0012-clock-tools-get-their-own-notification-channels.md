# Clock tools get their own notification channels

The alarm path had two channels and a fixed pair of notification ids, both created lazily, and the map in
issue #5 left "notification design" open: with a timer that rings, a timer that runs, a stopwatch that runs
and a timer that was missed, someone had to decide how many channels exist, what each one is worth, and how
they coexist with the upcoming-alarm notification. This ADR answers that for the four new surfaces and
changes nothing about the two that already exist.

**`alarm_ringing` does not acquire a second meaning, and the reason is the evidence.** That channel carries
the high-importance notification whose `full-screenIntent` launches `RingingActivity`, which is the only
place evidence can be recorded (`AGENTS.md` §7, §10). If timers rang on it, a user who wanted quieter timers
would have to demote the one channel that produces alarm evidence, and "silence timers, keep alarms" would
become impossible in a product whose promise is the alarm. Users cannot demote a channel they were not
offered, so the fix is a separate channel: `timer_ringing`, `IMPORTANCE_HIGH`, `CATEGORY_ALARM`, sound null,
vibration off, `VISIBILITY_PUBLIC`, its own `full-screenIntent` and content intent pointing at
`TimerRingingActivity`. Its sound comes from `TimerPlaybackService`, exactly as the alarm channel's comes
from `AlarmPlaybackService`; the channel only carries the surface.

**One low channel serves both clock tools.** A running timer and a running stopwatch are the same kind of
object: ongoing, silent, derived from a stored anchor, and backed by no service (`AGENTS.md` §5a). Giving
them one shared `clock_tool_ongoing` channel (`IMPORTANCE_LOW`) is honest about that, and it keeps the
settings screen to five entries instead of six. AOSP DeskClock's own split, one low channel for the running
timer and another for the stopwatch, shows that one low channel per ongoing surface is the norm, not a
compromise. A missed timer is the third low surface and keeps its own channel, `timer_missed`, because
ADR 0008 already decided that a missed timer is a low-importance notice with a different job from a running
one: Reset or delete clears it.

| Channel id | Importance | Posted by |
|---|---|---|
| `alarm_ringing` | HIGH | a ringing alarm (unchanged) |
| `alarm_upcoming` | LOW | the upcoming-alarm nudge (unchanged) |
| `timer_ringing` | HIGH | a timer that reached zero |
| `clock_tool_ongoing` | LOW | a running timer or a running stopwatch |
| `timer_missed` | LOW | a timer found more than a minute overdue |

**Notification ids get their own block.** `1101` timer ringing, `1102` running timer, `1103` missed timer,
`1104` stopwatch. The alarm path keeps `1001` ringing and `1004` upcoming with `1002` and `1003` as its two
command request codes, so nothing collides and the two paths can be read apart in a `dumpsys notification`
dump.

**Every channel is created at startup.** `UpYetApplication.onCreate` calls `ensureChannels()`, which today
only happens on the first ring or the first upcoming nudge. That laziness is a visible gap on a fresh
install: the system settings screen has no row for a channel until something has posted on it, so a user
cannot quiet the timer channel before the first timer has ever rung. Creating a channel that exists is a
no-op, so eager creation costs nothing and makes the settings screen complete from the first launch.

**No notification group.** The app uses `setGroup` nowhere and keeps it that way. A grouped child is a child
the platform may collapse or hide, and the upcoming-alarm notification, which is the one persistent surface
of the alarm feature, would be swallowed together with the ringing one. Five ids that never group are five
notifications a user can always find.

**A timer ring never cancels the upcoming-alarm notification.** `AlarmPlaybackService` cancels it when an
alarm rings, because that occurrence has just passed and the nudge would be a lie. A timer ending says
nothing about the next alarm, so the timer path never calls `cancelUpcoming()`. Getting this wrong would
delete the next-alarm reminder at a random moment, which reads as the alarm feature being broken.

**The two ongoing notifications are chronometer-driven.** Both use `setUsesChronometer` with a `when`
timestamp, and the running timer also `setChronometerCountDown`, so the platform draws the counting text and
the app needs no service, no wake lock and no ticking coroutine for either (`AGENTS.md` §5a). A countdown
kept up to date by re-posting a notification every second would be an always-running process in disguise.

## Considered options

- **DeskClock's shape: one HIGH channel shared by ringing alarms and timers.** Rejected: cheapest and one
  knob for "things that make noise", but it lets timer preferences govern the channel that carries the
  alarm's evidence full-screen intent, and silently reframes an existing user's `alarm_ringing` settings.
- **One channel per surface: `timer_ringing`, `timer_running`, `stopwatch_running`, `timer_missed`.**
  Rejected: maximum control, four new rows in a clock app's notification settings, and four chances to
  demote the wrong one. The two ongoing tools are one kind of notification; one channel says so.
- **Lazy channel creation, as today.** Rejected: it hides the channel from system settings until first use,
  which is exactly when the user who wants to silence it is looking for it.
- **A notification group for the clock tools.** Rejected: grouping buys nothing the five ids do not already
  give, and it puts the upcoming-alarm notification at risk of being collapsed with a ring.
- **Reusing the alarm notification ids for the clock tools.** Rejected: `cancelUpcoming()` and the alarm's
  command request codes are keyed on those numbers, so a shared id would let a timer cancel an alarm.
- **Reading channel importance in the reliability check now.** Deferred, not rejected: `canUseFullScreenIntent()`
  is app-level and cannot see a demoted `timer_ringing`, so a check could catch it, but this ADR is about the
  channel layout and the check can land later without changing it.

## Consequences

Three new channel names and descriptions need strings in `values` and `values-es`, and the notification
builders for the four surfaces live with the timer and stopwatch paths, not in `AlarmNotifications` alongside
the alarm ones. `AlarmNotifications.ensureChannels()` grows the three new channels and is called once from
`UpYetApplication.onCreate`; `postUpcoming()` no longer needs its own `ensureChannels()` call but keeps it as
harmless defensive code or drops it, a slice-level choice.

`AGENTS.md` §8a already says `TimerPlaybackService` owns the timer ringing notification, so nothing in the
contract changes. The slice that lands the channels is independent of every timer feature and can ship first,
which is why it is one of the earliest build slices.

One claim is reasoned but unverified and needs a device: that a `timer_ringing` notification at
`IMPORTANCE_HIGH` with `CATEGORY_ALARM` is granted its full-screen intent on Android 14+ the way the alarm
channel is, which is the same open question ADR 0008 records for the timer ringing surface.
