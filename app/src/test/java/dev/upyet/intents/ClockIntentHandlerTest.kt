package dev.upyet.intents

import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.alarm.domain.SnoozeBudget
import dev.upyet.alarm.playback.RingingSession
import dev.upyet.alarm.playback.RingingSessionRegistry
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import dev.upyet.alarm.scheduling.SchedulingResult
import dev.upyet.core.time.TimerClock
import dev.upyet.settings.data.SettingsRepository
import dev.upyet.testing.FakeAlarmRepository
import dev.upyet.testing.FakeDataStore
import dev.upyet.testing.FakeTimerRepository
import dev.upyet.testing.FixedTimeProvider
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import dev.upyet.timer.domain.TimerTransition
import dev.upyet.timer.playback.RingingTimer
import dev.upyet.timer.playback.TimerRingingRegistry
import dev.upyet.timer.ringing.TimerRingingCommands
import dev.upyet.timer.scheduling.TimerEditor
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class ClockIntentHandlerTest {
    private val now = Instant.parse("2026-08-24T10:00:00Z")
    private val anchor = TimerAnchor(Duration.ofSeconds(1_000), Instant.parse("2026-10-26T10:00:00Z"))

    private fun alarm(
        id: Long,
        time: LocalTime = LocalTime.of(7, 30),
        enabled: Boolean = true,
        label: String = "",
        recurrence: Recurrence = Recurrence.OneTime,
        skipNextOn: LocalDate? = null,
        voice: Boolean = false,
    ) = Alarm(
        id = AlarmId(id),
        time = time,
        enabled = enabled,
        label = label,
        recurrence = recurrence,
        soundUri = null,
        vibrationEnabled = true,
        snoozeMinutes = 9,
        evidenceEnabled = false,
        skipNextOn = skipNextOn,
        voice = voice,
        createdAt = now,
        updatedAt = now,
    )

    private fun timer(id: Long, state: TimerRunState, evidence: Boolean = false) =
        Timer.restore(TimerId(id), Duration.ofMinutes(5), "", null, evidence, state, anchor.wallClock)

    private fun ringingTimer(id: Long, evidence: Boolean) = RingingTimer(TimerId(id), "", Duration.ofMinutes(5), null, evidence, now)

    private class FakeAlarmActions : AlarmActions {
        var scheduleResult: SchedulingResult = SchedulingResult.Scheduled
        var skipResult: SchedulingResult? = SchedulingResult.Scheduled
        val scheduled = mutableListOf<Alarm>()
        val skipped = mutableListOf<Alarm>()
        val turnedOff = mutableListOf<Alarm>()

        override suspend fun schedule(alarm: Alarm): SchedulingResult {
            scheduled += alarm
            return scheduleResult
        }

        override suspend fun skipNext(alarm: Alarm): SchedulingResult? {
            skipped += alarm
            return skipResult
        }

        override suspend fun turnOff(alarm: Alarm) {
            turnedOff += alarm
        }
    }

    private class FakeSnoozedAlarms(var map: Map<AlarmId, Instant> = emptyMap()) : SnoozedAlarms {
        override fun snoozed(now: Instant): Map<AlarmId, Instant> = map
    }

    private class FakeTimerEditor(private val repository: FakeTimerRepository) : TimerEditor {
        val commands = mutableListOf<String>()
        val added = mutableListOf<Timer>()

        override suspend fun add(timer: Timer): TimerId {
            added += timer
            val id = TimerId((repository.all.maxOfOrNull { it.id.value } ?: 0L) + 1)
            repository.upsert(
                Timer.restore(id, timer.length, timer.label, timer.ringtoneUri, timer.evidenceEnabled, timer.runState, timer.createdAt),
            )
            return id
        }

        override suspend fun edit(
            id: TimerId,
            command: String,
            onlyIf: (Timer) -> Boolean,
            transition: (Timer, TimerAnchor) -> TimerTransition,
        ): TimerTransition {
            commands += command
            val timer = repository.getTimer(id) ?: return TimerTransition.NoOp
            if (timer.runState == TimerRunState.Ringing || !onlyIf(timer)) return TimerTransition.NoOp
            val result = transition(timer, TimerAnchor(Duration.ZERO, Instant.EPOCH))
            if (result is TimerTransition.Applied) repository.upsert(result.timer)
            return result
        }
    }

    private class FakeCommands : TimerRingingCommands {
        val stops = mutableListOf<TimerId>()

        override fun stop(id: TimerId) {
            stops += id
        }

        override fun addMinute(id: TimerId) = Unit

        override fun stopAll() = Unit
    }

    private inner class Harness(alarmList: List<Alarm> = emptyList(), timerList: List<Timer> = emptyList()) {
        val alarms = FakeAlarmRepository(alarmList)
        val actions = FakeAlarmActions()
        val snoozed = FakeSnoozedAlarms()
        val alarmRinging = RingingSessionRegistry()
        val timers = FakeTimerRepository(timerList)
        val editor = FakeTimerEditor(timers)
        val timerRinging = TimerRingingRegistry()
        val commands = FakeCommands()
        val settings = SettingsRepository(FakeDataStore())
        val handler = ClockIntentHandler(
            alarms,
            actions,
            snoozed,
            alarmRinging,
            timers,
            editor,
            timerRinging,
            commands,
            settings,
            FixedTimeProvider(now),
            TimerClock { anchor },
        )

        fun ring(id: Long) = alarmRinging.update(
            RingingSession(
                alarmId = AlarmId(id),
                occurrenceId = null,
                scheduledFor = now,
                triggeredAt = now,
                label = null,
                kind = AlarmOccurrenceKind.MAIN,
                snoozeMinutes = 9,
                evidenceEnabled = false,
                isUserUnlocked = true,
                budget = SnoozeBudget(1),
                chainStartedAt = now,
            ),
        )
    }

    private fun setAlarm(
        time: LocalTime? = LocalTime.of(6, 45),
        recurrence: Recurrence = Recurrence.OneTime,
        label: String = "",
        sound: AlarmSound = AlarmSound.Default,
        vibrate: Boolean? = null,
        skipUi: Boolean = true,
    ) = ClockIntent.SetAlarm(time, recurrence, label, sound, vibrate, skipUi)

    private fun toast(@androidx.annotation.StringRes id: Int, time: LocalTime? = null) = IntentEffect.Toast(id, time)

    // SET_ALARM

    @Test
    fun `set alarm without a time opens a blank editor`() = runTest {
        val h = Harness()
        assertThat(h.handler.handle(setAlarm(time = null))).containsExactly(IntentEffect.OpenAlarmEditor(null))
    }

    @Test
    fun `set alarm without skip ui prefills the editor and writes nothing`() = runTest {
        val h = Harness()
        val effects = h.handler.handle(setAlarm(label = "Gym", sound = AlarmSound.Silent, vibrate = false, skipUi = false))
        assertThat(effects).containsExactly(
            IntentEffect.OpenAlarmEditor(AlarmPrefill(LocalTime.of(6, 45), Recurrence.OneTime, "Gym", AlarmSound.Silent, false)),
        )
        assertThat(h.alarms.stored).isEmpty()
        assertThat(h.actions.scheduled).isEmpty()
    }

    @Test
    fun `set alarm with skip ui creates a voice alarm from settings defaults and schedules it`() = runTest {
        val h = Harness()
        h.settings.setEvidenceEnabledByDefault(true)
        val effects = h.handler.handle(setAlarm(sound = AlarmSound.Uri("content://x"), vibrate = null))
        val stored = h.alarms.stored.single()
        assertThat(stored.enabled).isTrue()
        assertThat(stored.voice).isTrue()
        assertThat(stored.soundUri).isEqualTo("content://x")
        assertThat(stored.soundEnabled).isTrue()
        assertThat(stored.evidenceEnabled).isTrue()
        assertThat(stored.createdAt).isEqualTo(now)
        assertThat(h.actions.scheduled.single().id).isEqualTo(stored.id)
        assertThat(effects).containsExactly(toast(R.string.intent_alarm_set, LocalTime.of(6, 45)))
    }

    @Test
    fun `set alarm silent sound turns sound off and recurring alarms are not voice alarms`() = runTest {
        val h = Harness()
        h.handler.handle(setAlarm(recurrence = Recurrence.Daily, sound = AlarmSound.Silent, vibrate = false))
        val stored = h.alarms.stored.single()
        assertThat(stored.soundEnabled).isFalse()
        assertThat(stored.vibrationEnabled).isFalse()
        assertThat(stored.voice).isFalse()
    }

    @Test
    fun `set alarm skip ui duplicate is reused with no new row and voice unchanged`() = runTest {
        val existing = alarm(
            4,
            LocalTime.of(6, 45),
            enabled = false,
            label = "Gym",
            recurrence = Recurrence.Daily,
            skipNextOn = LocalDate.of(2026, 8, 25),
            voice = false,
        )
        val h = Harness(listOf(existing))
        val effects = h.handler.handle(setAlarm(recurrence = Recurrence.Daily, label = "Gym"))
        val stored = h.alarms.stored.single()
        assertThat(stored.id).isEqualTo(AlarmId(4))
        assertThat(stored.enabled).isTrue()
        assertThat(stored.skipNextOn).isNull()
        assertThat(stored.voice).isFalse()
        assertThat(h.actions.scheduled.single().skipNextOn).isNull()
        assertThat(effects).containsExactly(toast(R.string.intent_alarm_set, LocalTime.of(6, 45)))
    }

    @Test
    fun `set alarm exact alarms unavailable disables the alarm and opens reliability`() = runTest {
        val h = Harness()
        h.actions.scheduleResult = SchedulingResult.ExactAlarmsUnavailable
        val effects = h.handler.handle(setAlarm())
        assertThat(h.alarms.stored.single().enabled).isFalse()
        assertThat(effects).containsExactly(toast(R.string.alarm_scheduling_exact_unavailable), IntentEffect.OpenReliability).inOrder()
    }

    @Test
    fun `set alarm failure disables the alarm and opens reliability`() = runTest {
        val h = Harness()
        h.actions.scheduleResult = SchedulingResult.Failed(IllegalStateException())
        val effects = h.handler.handle(setAlarm())
        assertThat(h.alarms.stored.single().enabled).isFalse()
        assertThat(effects).containsExactly(toast(R.string.alarm_scheduling_failed), IntentEffect.OpenReliability).inOrder()
    }

    // Show, invalid, unsupported

    @Test
    fun `show alarms and show timers open their tabs`() = runTest {
        val h = Harness()
        assertThat(h.handler.handle(ClockIntent.ShowAlarms)).containsExactly(IntentEffect.OpenAlarms)
        assertThat(h.handler.handle(ClockIntent.ShowTimers)).containsExactly(IntentEffect.OpenTimers)
    }

    @Test
    fun `invalid toasts and unsupported does nothing`() = runTest {
        val h = Harness()
        assertThat(h.handler.handle(ClockIntent.Invalid("x"))).containsExactly(toast(R.string.intent_invalid_request))
        assertThat(h.handler.handle(ClockIntent.Unsupported)).isEmpty()
    }

    // SET_TIMER

    @Test
    fun `set timer without a length opens the timer tab`() = runTest {
        val h = Harness()
        assertThat(h.handler.handle(ClockIntent.SetTimer(null, "", true))).containsExactly(IntentEffect.OpenTimers)
        assertThat(h.editor.added).isEmpty()
    }

    @Test
    fun `set timer with skip ui starts a running timer and toasts`() = runTest {
        val h = Harness()
        val effects = h.handler.handle(ClockIntent.SetTimer(Duration.ofMinutes(10), "Tea", true))
        val added = h.editor.added.single()
        assertThat(added.label).isEqualTo("Tea")
        assertThat(added.runState).isInstanceOf(TimerRunState.Running::class.java)
        assertThat(effects).containsExactly(toast(R.string.intent_timer_started))
    }

    @Test
    fun `set timer without skip ui starts it and opens the timer tab`() = runTest {
        val h = Harness()
        val effects = h.handler.handle(ClockIntent.SetTimer(Duration.ofMinutes(10), "", false))
        assertThat(h.editor.added).hasSize(1)
        assertThat(effects).containsExactly(IntentEffect.OpenTimers)
    }

    @Test
    fun `set timer takes the global evidence value`() = runTest {
        val on = Harness()
        on.settings.setTimerEvidenceByDefault(true)
        on.handler.handle(ClockIntent.SetTimer(Duration.ofMinutes(1), "", true))
        assertThat(on.editor.added.single().evidenceEnabled).isTrue()

        val off = Harness()
        off.settings.setTimerEvidenceByDefault(false)
        off.handler.handle(ClockIntent.SetTimer(Duration.ofMinutes(1), "", true))
        assertThat(off.editor.added.single().evidenceEnabled).isFalse()
    }

    // DISMISS_TIMER

    @Test
    fun `dismiss timer with none ringing toasts`() = runTest {
        val h = Harness()
        assertThat(h.handler.handle(ClockIntent.DismissTimer(null))).containsExactly(toast(R.string.intent_timer_none_ringing))
    }

    @Test
    fun `dismiss timer without id stops every recording-off ringing timer`() = runTest {
        val h = Harness()
        h.timerRinging.add(ringingTimer(1, false))
        h.timerRinging.add(ringingTimer(2, false))
        val effects = h.handler.handle(ClockIntent.DismissTimer(null))
        assertThat(h.commands.stops).containsExactly(TimerId(1), TimerId(2))
        assertThat(effects).containsExactly(toast(R.string.intent_timer_stopped))
    }

    @Test
    fun `dismiss timer without id mixed stops only the recording-off one and brings the screen forward`() = runTest {
        val h = Harness()
        h.timerRinging.add(ringingTimer(1, true))
        h.timerRinging.add(ringingTimer(2, false))
        val effects = h.handler.handle(ClockIntent.DismissTimer(null))
        assertThat(h.commands.stops).containsExactly(TimerId(2))
        assertThat(effects).containsExactly(IntentEffect.BringTimerRingingForward)
    }

    @Test
    fun `dismiss timer by id never stops a recording ring`() = runTest {
        val h = Harness()
        h.timerRinging.add(ringingTimer(1, true))
        assertThat(h.handler.handle(ClockIntent.DismissTimer(1))).containsExactly(IntentEffect.BringTimerRingingForward)
        assertThat(h.commands.stops).isEmpty()
    }

    @Test
    fun `dismiss timer by id stops a recording-off ring`() = runTest {
        val h = Harness()
        h.timerRinging.add(ringingTimer(1, false))
        assertThat(h.handler.handle(ClockIntent.DismissTimer(1))).containsExactly(toast(R.string.intent_timer_stopped))
        assertThat(h.commands.stops).containsExactly(TimerId(1))
    }

    @Test
    fun `dismiss timer unknown id is not found`() = runTest {
        val h = Harness()
        assertThat(h.handler.handle(ClockIntent.DismissTimer(9))).containsExactly(toast(R.string.intent_timer_not_found))
    }

    @Test
    fun `dismiss timer stored as ringing but missing from the registry follows its evidence flag`() = runTest {
        val h =
            Harness(timerList = listOf(timer(1, TimerRunState.Ringing, evidence = true), timer(2, TimerRunState.Ringing, evidence = false)))
        assertThat(h.handler.handle(ClockIntent.DismissTimer(1))).containsExactly(IntentEffect.BringTimerRingingForward)
        assertThat(h.handler.handle(ClockIntent.DismissTimer(2))).containsExactly(toast(R.string.intent_timer_stopped))
        assertThat(h.commands.stops).containsExactly(TimerId(2))
    }

    @Test
    fun `dismiss timer resets running paused and missed timers`() = runTest {
        val running = TimerRunState.Running(Duration.ofMinutes(5), anchor)
        val h = Harness(
            timerList = listOf(
                timer(1, running),
                timer(2, TimerRunState.Paused(Duration.ofMinutes(1))),
                timer(3, TimerRunState.Missed),
            ),
        )
        for (id in 1L..3L) {
            assertThat(h.handler.handle(ClockIntent.DismissTimer(id))).containsExactly(toast(R.string.intent_timer_reset))
            assertThat(h.timers.getTimer(TimerId(id))!!.runState).isEqualTo(TimerRunState.Idle)
        }
        assertThat(h.editor.commands).containsExactly("intent_dismiss", "intent_dismiss", "intent_dismiss")
    }

    @Test
    fun `dismiss timer idle needs no edit`() = runTest {
        val h = Harness(timerList = listOf(timer(1, TimerRunState.Idle)))
        assertThat(h.handler.handle(ClockIntent.DismissTimer(1))).containsExactly(toast(R.string.intent_timer_reset))
        assertThat(h.editor.commands).isEmpty()
    }

    // DISMISS_ALARM

    @Test
    fun `dismiss alarm with no match toasts`() = runTest {
        val h = Harness(listOf(alarm(1)))
        val effects = h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Label("nothing")))
        assertThat(effects).containsExactly(toast(R.string.intent_alarm_none_matched))
    }

    @Test
    fun `dismiss alarm with no alarms at all matches nothing`() = runTest {
        val h = Harness()
        assertThat(h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.None))).containsExactly(toast(R.string.intent_alarm_none_matched))
    }

    @Test
    fun `dismiss alarm with several matches changes nothing and opens alarms`() = runTest {
        val h = Harness(listOf(alarm(1), alarm(2, LocalTime.of(8, 0))))
        val effects = h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.All))
        assertThat(effects).containsExactly(toast(R.string.intent_alarm_several_matched), IntentEffect.OpenAlarms).inOrder()
        assertThat(h.actions.turnedOff).isEmpty()
        assertThat(h.actions.skipped).isEmpty()
        assertThat(h.alarms.enabledChanges).isEmpty()
        assertThat(h.alarms.deleted).isEmpty()
    }

    @Test
    fun `dismiss alarm all with one match acts on it`() = runTest {
        val a = alarm(1)
        val h = Harness(listOf(a, alarm(2, enabled = false)))
        val effects = h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.All))
        assertThat(h.actions.turnedOff).containsExactly(a)
        assertThat(effects).containsExactly(toast(R.string.intent_alarm_turned_off, a.time))
    }

    @Test
    fun `dismiss alarm on a ringing alarm brings the screen forward`() = runTest {
        val h = Harness(listOf(alarm(1, recurrence = Recurrence.Daily)))
        h.ring(1)
        val effects = h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.None))
        assertThat(effects).containsExactly(IntentEffect.BringAlarmRingingForward)
        assertThat(h.actions.skipped).isEmpty()
    }

    @Test
    fun `dismiss alarm on a ringing one-time alarm does not turn it off`() = runTest {
        val h = Harness(listOf(alarm(1)))
        h.ring(1)
        val effects = h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.None))
        assertThat(effects).containsExactly(IntentEffect.BringAlarmRingingForward)
        assertThat(h.actions.turnedOff).isEmpty()
        assertThat(h.alarms.enabledChanges).isEmpty()
    }

    @Test
    fun `dismiss alarm includes a ringing alarm that is already disabled`() = runTest {
        val h = Harness(listOf(alarm(1, enabled = false)))
        h.ring(1)
        assertThat(h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.None))).containsExactly(IntentEffect.BringAlarmRingingForward)
    }

    @Test
    fun `dismiss alarm on a snoozed alarm changes nothing`() = runTest {
        val h = Harness(listOf(alarm(1)))
        h.snoozed.map = mapOf(AlarmId(1) to now.plusSeconds(300))
        val effects = h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.None))
        assertThat(effects).containsExactly(toast(R.string.intent_alarm_snoozed))
        assertThat(h.actions.turnedOff).isEmpty()
        assertThat(h.actions.skipped).isEmpty()
    }

    @Test
    fun `dismiss alarm skips the next occurrence of a recurring alarm`() = runTest {
        val a = alarm(1, recurrence = Recurrence.Daily)
        val h = Harness(listOf(a))
        val effects = h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.None))
        assertThat(h.actions.skipped).containsExactly(a)
        assertThat(effects).containsExactly(toast(R.string.intent_alarm_skipped, a.time))
    }

    @Test
    fun `dismiss alarm skip with nothing to skip reports no match`() = runTest {
        val h = Harness(listOf(alarm(1, recurrence = Recurrence.Daily)))
        h.actions.skipResult = null
        assertThat(h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.None))).containsExactly(toast(R.string.intent_alarm_none_matched))
    }

    @Test
    fun `dismiss alarm skip failures point at reliability`() = runTest {
        val h = Harness(listOf(alarm(1, recurrence = Recurrence.Daily)))
        h.actions.skipResult = SchedulingResult.ExactAlarmsUnavailable
        assertThat(h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.None)))
            .containsExactly(toast(R.string.alarm_scheduling_exact_unavailable), IntentEffect.OpenReliability).inOrder()
        h.actions.skipResult = SchedulingResult.Failed(IllegalStateException())
        assertThat(h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.None)))
            .containsExactly(toast(R.string.alarm_scheduling_failed), IntentEffect.OpenReliability).inOrder()
    }

    @Test
    fun `dismiss alarm turns off a one-time alarm and never deletes it`() = runTest {
        val a = alarm(1)
        val h = Harness(listOf(a))
        val effects = h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.None))
        assertThat(h.actions.turnedOff).containsExactly(a)
        assertThat(h.alarms.deleted).isEmpty()
        assertThat(effects).containsExactly(toast(R.string.intent_alarm_turned_off, a.time))
    }

    @Test
    fun `dismiss alarm next prefers the ringing alarm`() = runTest {
        val h = Harness(listOf(alarm(1, LocalTime.of(10, 30)), alarm(2, LocalTime.of(23, 0))))
        h.ring(2)
        assertThat(h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Next))).containsExactly(IntentEffect.BringAlarmRingingForward)
    }

    @Test
    fun `dismiss alarm next picks the earliest next ring`() = runTest {
        val soon = alarm(1, LocalTime.of(10, 30))
        val h = Harness(listOf(alarm(2, LocalTime.of(23, 0)), soon, alarm(3, LocalTime.of(5, 0))))
        h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Next))
        assertThat(h.actions.turnedOff).containsExactly(soon)
    }

    @Test
    fun `dismiss alarm next counts a snooze and a skipped day`() = runTest {
        val snoozedAlarm = alarm(1, LocalTime.of(23, 0))
        val skipped = alarm(2, LocalTime.of(10, 30), recurrence = Recurrence.Daily, skipNextOn = LocalDate.of(2026, 8, 24))
        val h = Harness(listOf(skipped, snoozedAlarm))
        h.snoozed.map = mapOf(AlarmId(1) to now.plusSeconds(60))
        // Alarm 1 rings again in a minute, before alarm 2's next ring tomorrow, and it is snoozed, so nothing changes.
        val effects = h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Next))
        assertThat(effects).containsExactly(toast(R.string.intent_alarm_snoozed))
    }

    @Test
    fun `dismiss alarm next with no alarms matches nothing`() = runTest {
        val h = Harness()
        assertThat(h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Next))).containsExactly(toast(R.string.intent_alarm_none_matched))
    }

    @Test
    fun `dismiss alarm time with am pm and minutes`() = runTest {
        val h = Harness(listOf(alarm(1, LocalTime.of(7, 30)), alarm(2, LocalTime.of(19, 30)), alarm(3, LocalTime.of(7, 0))))
        h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Time(7, 30, isPm = true)))
        assertThat(h.actions.turnedOff.map { it.id }).containsExactly(AlarmId(2))
        h.actions.turnedOff.clear()
        h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Time(7, 30, isPm = false)))
        assertThat(h.actions.turnedOff.map { it.id }).containsExactly(AlarmId(1))
    }

    @Test
    fun `dismiss alarm time 12 am is midnight and 12 pm is noon`() = runTest {
        val h = Harness(listOf(alarm(1, LocalTime.of(0, 0)), alarm(2, LocalTime.of(12, 0))))
        h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Time(12, null, isPm = false)))
        assertThat(h.actions.turnedOff.map { it.id }).containsExactly(AlarmId(1))
        h.actions.turnedOff.clear()
        h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Time(12, null, isPm = true)))
        assertThat(h.actions.turnedOff.map { it.id }).containsExactly(AlarmId(2))
    }

    @Test
    fun `dismiss alarm time 7 with no meridiem matches 19 00`() = runTest {
        val h = Harness(listOf(alarm(1, LocalTime.of(19, 0))))
        h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Time(7, null, isPm = null)))
        assertThat(h.actions.turnedOff.map { it.id }).containsExactly(AlarmId(1))
    }

    @Test
    fun `dismiss alarm time with a 24 hour value and minutes only`() = runTest {
        val h = Harness(listOf(alarm(1, LocalTime.of(15, 10)), alarm(2, LocalTime.of(15, 20))))
        h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Time(15, null, isPm = null)))
        assertThat(h.actions.turnedOff).isEmpty()
        h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Time(null, 20, isPm = null)))
        assertThat(h.actions.turnedOff.map { it.id }).containsExactly(AlarmId(2))
    }

    @Test
    fun `dismiss alarm label match ignores case and surrounding spaces`() = runTest {
        val h = Harness(listOf(alarm(1, label = " Gym "), alarm(2, label = "Work")))
        h.handler.handle(ClockIntent.DismissAlarm(AlarmSearch.Label("gym")))
        assertThat(h.actions.turnedOff.map { it.id }).containsExactly(AlarmId(1))
    }
}
