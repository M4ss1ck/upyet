package dev.upyet.intents

import dev.upyet.R
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.NextOccurrenceCalculator
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.alarm.playback.RingingSessionRegistry
import dev.upyet.alarm.scheduling.SchedulingResult
import dev.upyet.core.time.TimeProvider
import dev.upyet.core.time.TimerClock
import dev.upyet.settings.data.SettingsRepository
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRepository
import dev.upyet.timer.domain.TimerRunState
import dev.upyet.timer.domain.TimerTransition
import dev.upyet.timer.playback.TimerRingingRegistry
import dev.upyet.timer.ringing.TimerRingingCommands
import dev.upyet.timer.scheduling.TimerEditor
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

/** The seam the trampoline's ViewModel depends on, so the once-only guarantee is testable without the full handler. */
fun interface ClockIntentHandling {
    suspend fun handle(intent: ClockIntent): List<IntentEffect>
}

/**
 * Acts on a parsed [ClockIntent] and returns what the visible trampoline activity should do next. It owns the
 * ADR 0007 rules: an assistant never ends a wake-up (a ringing or snoozed alarm is left alone, a recording
 * timer keeps ringing), never deletes an alarm, and never claims success when scheduling failed (AGENTS.md §2
 * inv. 10). It touches no Android type, so it is plain JVM-testable.
 */
class ClockIntentHandler @Inject constructor(
    private val alarms: AlarmRepository,
    private val alarmActions: AlarmActions,
    private val snoozedAlarms: SnoozedAlarms,
    private val alarmRinging: RingingSessionRegistry,
    private val timers: TimerRepository,
    private val timerEditor: TimerEditor,
    private val timerRinging: TimerRingingRegistry,
    private val timerCommands: TimerRingingCommands,
    private val settings: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val timerClock: TimerClock,
) : ClockIntentHandling {
    override suspend fun handle(intent: ClockIntent): List<IntentEffect> = when (intent) {
        is ClockIntent.SetAlarm -> setAlarm(intent)
        ClockIntent.ShowAlarms -> listOf(IntentEffect.OpenAlarms)
        ClockIntent.ShowTimers -> listOf(IntentEffect.OpenTimers)
        is ClockIntent.SetTimer -> setTimer(intent)
        is ClockIntent.DismissTimer -> dismissTimer(intent.timerId)
        is ClockIntent.DismissAlarm -> dismissAlarm(intent.search)
        is ClockIntent.Invalid -> listOf(IntentEffect.Toast(R.string.intent_invalid_request))
        ClockIntent.Unsupported -> emptyList()
    }

    // region SET_ALARM

    private suspend fun setAlarm(intent: ClockIntent.SetAlarm): List<IntentEffect> {
        val time = intent.time ?: return listOf(IntentEffect.OpenAlarmEditor(null))
        if (!intent.skipUi) {
            return listOf(
                IntentEffect.OpenAlarmEditor(AlarmPrefill(time, intent.recurrence, intent.label, intent.sound, intent.vibrate)),
            )
        }
        val existing = alarms.observeAlarms().first().firstOrNull {
            it.time == time && it.recurrence == intent.recurrence && it.label == intent.label
        }
        val id: AlarmId
        val result: SchedulingResult
        if (existing != null) {
            // A repeated request reuses the row instead of piling up duplicates. Its voice flag stays as it was: a
            // reused alarm never becomes a voice alarm, so it is never deleted behind the user's back.
            if (existing.skipNextOn != null) alarms.setSkipNextOn(existing.id, null)
            alarms.setEnabled(existing.id, true)
            id = existing.id
            result = alarmActions.schedule(existing.copy(enabled = true, skipNextOn = null))
        } else {
            val s = settings.settings.first()
            val now = timeProvider.now()
            val alarm = Alarm(
                id = AlarmId(0),
                time = time,
                enabled = true,
                label = intent.label,
                recurrence = intent.recurrence,
                soundUri = (intent.sound as? AlarmSound.Uri)?.value,
                soundEnabled = intent.sound != AlarmSound.Silent,
                vibrationEnabled = intent.vibrate ?: s.defaultVibrationEnabled,
                snoozeMinutes = s.defaultSnoozeMinutes,
                evidenceEnabled = s.evidenceEnabledByDefault,
                voice = intent.recurrence == Recurrence.OneTime,
                createdAt = now,
                updatedAt = now,
            )
            id = alarms.upsert(alarm)
            result = alarmActions.schedule(alarm.copy(id = id))
        }
        return when (result) {
            SchedulingResult.Scheduled -> listOf(IntentEffect.Toast(R.string.intent_alarm_set, time))

            else -> {
                alarms.setEnabled(id, false)
                schedulingFailure(result)
            }
        }
    }

    private fun schedulingFailure(result: SchedulingResult): List<IntentEffect> {
        val message = if (result == SchedulingResult.ExactAlarmsUnavailable) {
            R.string.alarm_scheduling_exact_unavailable
        } else {
            R.string.alarm_scheduling_failed
        }
        return listOf(IntentEffect.Toast(message), IntentEffect.OpenReliability)
    }

    // endregion

    // region SET_TIMER

    private suspend fun setTimer(intent: ClockIntent.SetTimer): List<IntentEffect> {
        val length = intent.length ?: return listOf(IntentEffect.OpenTimers)
        // ADR 0010: a SET_TIMER timer takes the global Timer evidence value at creation.
        val evidence = settings.settings.first().timerEvidenceByDefault
        val now = timerClock.now()
        val created = Timer.create(
            id = TimerId(0),
            length = length,
            label = intent.label,
            ringtoneUri = null,
            evidenceEnabled = evidence,
            createdAt = now.wallClock,
        )
        val started = created.start(now) as? TimerTransition.Applied ?: return listOf(IntentEffect.Toast(R.string.intent_invalid_request))
        timerEditor.add(started.timer)
        return if (intent.skipUi) listOf(IntentEffect.Toast(R.string.intent_timer_started)) else listOf(IntentEffect.OpenTimers)
    }

    // endregion

    // region DISMISS_TIMER

    private suspend fun dismissTimer(timerId: Long?): List<IntentEffect> {
        val ringing = timerRinging.ringing.value
        if (timerId == null) {
            if (ringing.isEmpty()) return listOf(IntentEffect.Toast(R.string.intent_timer_none_ringing))
            return stopOrBringForward(ringing.map { it.id to it.evidenceEnabled })
        }
        val id = TimerId(timerId)
        ringing.firstOrNull { it.id == id }?.let { return stopOrBringForward(listOf(it.id to it.evidenceEnabled)) }
        val timer = timers.getTimer(id) ?: return listOf(IntentEffect.Toast(R.string.intent_timer_not_found))
        return when (timer.runState) {
            // The registry lags the store for a moment after a ring starts; the stored flag decides the same way.
            TimerRunState.Ringing -> stopOrBringForward(listOf(id to timer.evidenceEnabled))

            TimerRunState.Idle -> listOf(IntentEffect.Toast(R.string.intent_timer_reset))

            is TimerRunState.Running, is TimerRunState.Paused, TimerRunState.Missed -> {
                val result = timerEditor.edit(id, "intent_dismiss") { t, _ -> t.reset() }
                val message = if (result is TimerTransition.Applied) R.string.intent_timer_reset else R.string.intent_timer_not_found
                listOf(IntentEffect.Toast(message))
            }
        }
    }

    /**
     * A ringing timer with evidence off stops now; one with evidence on keeps ringing and its screen is brought
     * forward, so only Stop or +1 min on that screen ends it (ADR 0007 amendment, AGENTS.md §10).
     */
    private fun stopOrBringForward(ringing: List<Pair<TimerId, Boolean>>): List<IntentEffect> {
        ringing.filter { !it.second }.forEach { timerCommands.stop(it.first) }
        return if (ringing.any { it.second }) {
            listOf(IntentEffect.BringTimerRingingForward)
        } else {
            listOf(IntentEffect.Toast(R.string.intent_timer_stopped))
        }
    }

    // endregion

    // region DISMISS_ALARM

    private suspend fun dismissAlarm(search: AlarmSearch): List<IntentEffect> {
        val now = timeProvider.now()
        val zone = timeProvider.zone()
        val enabled = alarms.observeAlarms().first().filter { it.enabled }
        val ringingId = alarmRinging.session.value?.alarmId
        // A ringing alarm may already be disabled (a one-time alarm is retired when it fires) but is still in play.
        val ringingAlarm = ringingId?.let { id -> enabled.firstOrNull { it.id == id } ?: alarms.getAlarm(id) }
        val candidates = if (ringingAlarm != null && enabled.none { it.id == ringingAlarm.id }) enabled + ringingAlarm else enabled
        val snoozed = snoozedAlarms.snoozed(now)

        val matches = when (search) {
            AlarmSearch.None, AlarmSearch.All -> candidates
            AlarmSearch.Next -> listOfNotNull(ringingAlarm ?: earliest(candidates, snoozed, zone, now))
            is AlarmSearch.Time -> candidates.filter { matchesTime(it.time, search) }
            is AlarmSearch.Label -> candidates.filter { it.label.trim().equals(search.text, ignoreCase = true) }
        }
        return when (matches.size) {
            0 -> listOf(IntentEffect.Toast(R.string.intent_alarm_none_matched))

            // "Dismiss all my alarms" must not silently skip every wake-up, so nothing is changed (ADR 0007).
            1 -> actOn(matches.single(), ringingId, snoozed)

            else -> listOf(IntentEffect.Toast(R.string.intent_alarm_several_matched), IntentEffect.OpenAlarms)
        }
    }

    private fun earliest(candidates: List<Alarm>, snoozed: Map<AlarmId, Instant>, zone: java.time.ZoneId, now: Instant): Alarm? = candidates
        .mapNotNull { alarm ->
            val regular = NextOccurrenceCalculator.next(alarm.time, alarm.recurrence, zone, now, skipOn = alarm.skipNextOn)
            val nextRing = listOfNotNull(snoozed[alarm.id], regular).minOrNull()
            nextRing?.let { alarm to it }
        }.minByOrNull { it.second }
        ?.first

    private fun matchesTime(time: LocalTime, search: AlarmSearch.Time): Boolean {
        if (search.minutes != null && time.minute != search.minutes) return false
        val hour = search.hour ?: return true
        val hours = when (search.isPm) {
            true -> setOf(if (hour < 12) hour + 12 else hour)
            false -> setOf(if (hour == 12) 0 else hour)
            null -> if (hour in 1..12) setOf(hour % 12, hour % 12 + 12) else setOf(hour)
        }
        return time.hour in hours
    }

    private suspend fun actOn(alarm: Alarm, ringingId: AlarmId?, snoozed: Map<AlarmId, Instant>): List<IntentEffect> = when {
        // A wake-up in progress is never ended by an intent (ADR 0007): the ringing screen is brought forward.
        alarm.id == ringingId -> listOf(IntentEffect.BringAlarmRingingForward)

        alarm.id in snoozed -> listOf(IntentEffect.Toast(R.string.intent_alarm_snoozed))

        alarm.recurrence != Recurrence.OneTime -> when (val result = alarmActions.skipNext(alarm)) {
            SchedulingResult.Scheduled -> listOf(IntentEffect.Toast(R.string.intent_alarm_skipped, alarm.time))
            null -> listOf(IntentEffect.Toast(R.string.intent_alarm_none_matched))
            else -> schedulingFailure(result)
        }

        else -> {
            alarmActions.turnOff(alarm)
            listOf(IntentEffect.Toast(R.string.intent_alarm_turned_off, alarm.time))
        }
    }

    // endregion
}
