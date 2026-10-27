package dev.upyet.intents

import dev.upyet.alarm.domain.Recurrence
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/**
 * Action strings from `android.provider.AlarmClock`, copied as literals. The parser must run on the JVM, where the
 * Android constants are not on the classpath; the parser tests pin these values.
 */
object ClockIntentActions {
    const val SET_ALARM = "android.intent.action.SET_ALARM"
    const val SHOW_ALARMS = "android.intent.action.SHOW_ALARMS"
    const val SET_TIMER = "android.intent.action.SET_TIMER"
    const val SHOW_TIMERS = "android.intent.action.SHOW_TIMERS"
    const val DISMISS_TIMER = "android.intent.action.DISMISS_TIMER"
    const val DISMISS_ALARM = "android.intent.action.DISMISS_ALARM"
}

private const val EXTRA_PREFIX = "android.intent.extra.alarm."
private const val EXTRA_HOUR = "${EXTRA_PREFIX}HOUR"
private const val EXTRA_MINUTES = "${EXTRA_PREFIX}MINUTES"
private const val EXTRA_DAYS = "${EXTRA_PREFIX}DAYS"
private const val EXTRA_MESSAGE = "${EXTRA_PREFIX}MESSAGE"
private const val EXTRA_RINGTONE = "${EXTRA_PREFIX}RINGTONE"
private const val EXTRA_VIBRATE = "${EXTRA_PREFIX}VIBRATE"
private const val EXTRA_SKIP_UI = "${EXTRA_PREFIX}SKIP_UI"
private const val EXTRA_LENGTH = "${EXTRA_PREFIX}LENGTH"
private const val EXTRA_SEARCH_MODE = "${EXTRA_PREFIX}SEARCH_MODE"
private const val EXTRA_IS_PM = "${EXTRA_PREFIX}IS_PM"

private const val RINGTONE_SILENT = "silent"
private const val SEARCH_NEXT = "android.next"
private const val SEARCH_ALL = "android.all"
private const val SEARCH_LABEL = "android.label"
private const val SEARCH_TIME = "android.time"

private val HOUR_RANGE = 0..23
private val MINUTE_RANGE = 0..59

/** A timer longer than one day is rejected, matching the Timer tab's limit. */
private val TIMER_LENGTH_SECONDS = 1..86_400

/**
 * Parses one platform `AlarmClock` request into a [ClockIntent]. Pure: no Android types, no I/O, so every rule is
 * covered by a JVM unit test. Out-of-range extras yield [ClockIntent.Invalid]; an action this app does not serve
 * yields [ClockIntent.Unsupported].
 */
object ClockIntentParser {
    /** `java.util.Calendar` day constants (SUNDAY = 1), which `EXTRA_DAYS` carries, mapped to `java.time`. */
    private val CALENDAR_DAYS: Map<Int, DayOfWeek> = mapOf(
        1 to DayOfWeek.SUNDAY,
        2 to DayOfWeek.MONDAY,
        3 to DayOfWeek.TUESDAY,
        4 to DayOfWeek.WEDNESDAY,
        5 to DayOfWeek.THURSDAY,
        6 to DayOfWeek.FRIDAY,
        7 to DayOfWeek.SATURDAY,
    )

    fun parse(action: String?, data: String?, extras: IntentExtras): ClockIntent = when (action) {
        ClockIntentActions.SET_ALARM -> parseSetAlarm(action, extras)
        ClockIntentActions.SHOW_ALARMS -> ClockIntent.ShowAlarms
        ClockIntentActions.SET_TIMER -> parseSetTimer(action, extras)
        ClockIntentActions.SHOW_TIMERS -> ClockIntent.ShowTimers
        ClockIntentActions.DISMISS_TIMER -> parseDismissTimer(action, data)
        ClockIntentActions.DISMISS_ALARM -> parseDismissAlarm(action, extras)
        else -> ClockIntent.Unsupported
    }

    /**
     * Without an HOUR the caller asked for a blank editor, so every other extra is dropped on purpose: the editor
     * opens with its defaults and nothing is pre-filled.
     */
    private fun parseSetAlarm(action: String, extras: IntentExtras): ClockIntent {
        if (!extras.has(EXTRA_HOUR)) {
            return ClockIntent.SetAlarm(
                time = null,
                recurrence = Recurrence.OneTime,
                label = "",
                sound = AlarmSound.Default,
                vibrate = null,
                skipUi = false,
            )
        }
        val hour = extras.int(EXTRA_HOUR)?.takeIf { it in HOUR_RANGE } ?: return ClockIntent.Invalid(action)
        val minutes = optionalInt(extras, EXTRA_MINUTES, default = 0)?.takeIf { it in MINUTE_RANGE }
            ?: return ClockIntent.Invalid(action)
        return ClockIntent.SetAlarm(
            time = LocalTime.of(hour, minutes),
            recurrence = recurrenceFor(extras.intList(EXTRA_DAYS)),
            label = extras.string(EXTRA_MESSAGE).orEmpty().trim(),
            sound = soundFor(extras.string(EXTRA_RINGTONE)),
            vibrate = extras.boolean(EXTRA_VIBRATE),
            skipUi = extras.boolean(EXTRA_SKIP_UI) == true,
        )
    }

    /**
     * A countdown with no LENGTH opens the Timer tab. [ClockIntent.SetTimer.skipUi] is false there because there is
     * nothing to start, so `SKIP_UI` cannot apply.
     */
    private fun parseSetTimer(action: String, extras: IntentExtras): ClockIntent {
        if (!extras.has(EXTRA_LENGTH)) return ClockIntent.SetTimer(length = null, label = "", skipUi = false)
        val seconds = extras.int(EXTRA_LENGTH)?.takeIf { it in TIMER_LENGTH_SECONDS }
            ?: return ClockIntent.Invalid(action)
        return ClockIntent.SetTimer(
            length = Duration.ofSeconds(seconds.toLong()),
            label = extras.string(EXTRA_MESSAGE).orEmpty().trim(),
            skipUi = extras.boolean(EXTRA_SKIP_UI) == true,
        )
    }

    /**
     * The timer id is the last path segment of the data URI. A data URI without a numeric id is rejected rather than
     * treated as "every ringing timer", because that would dismiss timers the caller did not name.
     */
    private fun parseDismissTimer(action: String, data: String?): ClockIntent {
        if (data.isNullOrEmpty()) return ClockIntent.DismissTimer(timerId = null)
        val id = data.substringAfterLast('/').substringBefore('?').toLongOrNull()
            ?: return ClockIntent.Invalid(action)
        return ClockIntent.DismissTimer(timerId = id)
    }

    /** The data URI is ignored here, as DeskClock ignores it: alarm dismissal is selected by search mode only. */
    private fun parseDismissAlarm(action: String, extras: IntentExtras): ClockIntent {
        if (!extras.has(EXTRA_SEARCH_MODE)) return ClockIntent.DismissAlarm(AlarmSearch.None)
        val search = parseSearch(extras) ?: return ClockIntent.Invalid(action)
        return ClockIntent.DismissAlarm(search)
    }

    /** Null when the mode is unknown or its extras are out of range. The caller reports that as Invalid. */
    private fun parseSearch(extras: IntentExtras): AlarmSearch? = when (extras.string(EXTRA_SEARCH_MODE)) {
        SEARCH_NEXT -> AlarmSearch.Next
        SEARCH_ALL -> AlarmSearch.All
        SEARCH_LABEL -> extras.string(EXTRA_MESSAGE)?.trim()?.takeIf { it.isNotEmpty() }?.let { AlarmSearch.Label(it) }
        SEARCH_TIME -> timeSearch(extras)
        else -> null
    }

    /**
     * Hour and minutes are each optional, but at least one must be present. IS_PM is read only when present; a
     * wrong-typed value reads as absent, so the search matches both halves of the day.
     */
    private fun timeSearch(extras: IntentExtras): AlarmSearch? {
        val hasHour = extras.has(EXTRA_HOUR)
        val hasMinutes = extras.has(EXTRA_MINUTES)
        if (!hasHour && !hasMinutes) return null
        val hour = if (hasHour) {
            extras.int(EXTRA_HOUR)?.takeIf { it in HOUR_RANGE } ?: return null
        } else {
            null
        }
        val minutes = if (hasMinutes) {
            extras.int(EXTRA_MINUTES)?.takeIf { it in MINUTE_RANGE } ?: return null
        } else {
            null
        }
        val isPm = if (extras.has(EXTRA_IS_PM)) extras.boolean(EXTRA_IS_PM) else null
        return AlarmSearch.Time(hour = hour, minutes = minutes, isPm = isPm)
    }

    /** The default when [key] is absent, the value when it is an Int, and null when present but not an Int. */
    private fun optionalInt(extras: IntentExtras, key: String, default: Int): Int? = if (extras.has(key)) extras.int(key) else default

    /** Unknown calendar values are dropped. Nothing left means one-time; all seven days is [Recurrence.Daily]. */
    private fun recurrenceFor(calendarDays: List<Int>?): Recurrence {
        val days = calendarDays.orEmpty().mapNotNull { CALENDAR_DAYS[it] }.toSet()
        return when {
            days.isEmpty() -> Recurrence.OneTime
            days.size == DayOfWeek.entries.size -> Recurrence.Daily
            else -> Recurrence.Weekly(days)
        }
    }

    private fun soundFor(ringtone: String?): AlarmSound = when (ringtone) {
        null -> AlarmSound.Default
        RINGTONE_SILENT -> AlarmSound.Silent
        else -> AlarmSound.Uri(ringtone)
    }
}
