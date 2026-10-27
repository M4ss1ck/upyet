package dev.upyet.intents

import androidx.annotation.StringRes
import dev.upyet.alarm.domain.Recurrence
import java.time.Duration
import java.time.LocalTime

/**
 * One platform `AlarmClock` request, parsed and range-checked, with nothing Android left in it.
 *
 * `SNOOZE_ALARM` has no case on purpose: it is not declared (ADR 0007).
 */
sealed interface ClockIntent {
    /** `SET_ALARM`. [time] is null when the caller gave no hour, which opens a blank editor and ignores [skipUi]. */
    data class SetAlarm(
        val time: LocalTime?,
        val recurrence: Recurrence,
        val label: String,
        val sound: AlarmSound,
        val vibrate: Boolean?,
        val skipUi: Boolean,
    ) : ClockIntent

    data object ShowAlarms : ClockIntent

    /** `SET_TIMER`. [length] is null when the caller gave none, which opens the Timer tab and ignores [skipUi]. */
    data class SetTimer(val length: Duration?, val label: String, val skipUi: Boolean) : ClockIntent

    data object ShowTimers : ClockIntent

    /** `DISMISS_TIMER`. [timerId] comes from the data URI; null means "every ringing timer". */
    data class DismissTimer(val timerId: Long?) : ClockIntent

    /** `DISMISS_ALARM`, restricted by ADR 0007. The deeplink data URI is ignored, as DeskClock ignores it. */
    data class DismissAlarm(val search: AlarmSearch) : ClockIntent

    /** A recognised action whose extras are out of range (hour 24, minute 60, length 0 or over a day). */
    data class Invalid(val action: String) : ClockIntent

    /** An action this handler does not serve. */
    data object Unsupported : ClockIntent
}

/** `EXTRA_RINGTONE`: absent keeps the default sound, `VALUE_RINGTONE_SILENT` turns sound off. */
sealed interface AlarmSound {
    data object Default : AlarmSound
    data object Silent : AlarmSound
    data class Uri(val value: String) : AlarmSound
}

/** `EXTRA_ALARM_SEARCH_MODE`, plus [None] for "no mode given". */
sealed interface AlarmSearch {
    data object None : AlarmSearch
    data object Next : AlarmSearch
    data object All : AlarmSearch

    /** At least one of [hour] (0-23) and [minutes] is set. [isPm] null with an hour of 1-12 matches both halves. */
    data class Time(val hour: Int?, val minutes: Int?, val isPm: Boolean?) : AlarmSearch

    data class Label(val text: String) : AlarmSearch
}

/**
 * What the trampoline activity does after [ClockIntentHandler] has acted, in order. Every effect is something a
 * visible activity may do; none of them is a background `startActivity()` (AGENTS.md §7).
 */
sealed interface IntentEffect {
    /** [time] is formatted locale-aware by the activity and passed as the string's only argument. */
    data class Toast(@StringRes val message: Int, val time: LocalTime? = null) : IntentEffect

    data object OpenAlarms : IntentEffect
    data object OpenTimers : IntentEffect
    data object OpenReliability : IntentEffect

    /** Opens the alarm editor; [prefill] null is a blank new alarm. Nothing is saved until the user taps Save. */
    data class OpenAlarmEditor(val prefill: AlarmPrefill?) : IntentEffect

    /** Sends the alarm ringing notification's own full-screen/content intent, so `RingingActivity` comes forward. */
    data object BringAlarmRingingForward : IntentEffect

    /** Sends the timer ringing notification's own full-screen/content intent, so `TimerRingingActivity` comes forward. */
    data object BringTimerRingingForward : IntentEffect
}

/** The editor fields a `SET_ALARM` without `SKIP_UI` prefills. Settings defaults fill everything left null. */
data class AlarmPrefill(val time: LocalTime, val recurrence: Recurrence, val label: String, val sound: AlarmSound, val vibrate: Boolean?)
