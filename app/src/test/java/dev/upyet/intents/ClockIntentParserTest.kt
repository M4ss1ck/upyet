package dev.upyet.intents

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.Recurrence
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** The extras keys are spelled out here, not imported, so these tests pin the exact platform strings. */
private const val HOUR = "android.intent.extra.alarm.HOUR"
private const val MINUTES = "android.intent.extra.alarm.MINUTES"
private const val DAYS = "android.intent.extra.alarm.DAYS"
private const val MESSAGE = "android.intent.extra.alarm.MESSAGE"
private const val RINGTONE = "android.intent.extra.alarm.RINGTONE"
private const val VIBRATE = "android.intent.extra.alarm.VIBRATE"
private const val SKIP_UI = "android.intent.extra.alarm.SKIP_UI"
private const val LENGTH = "android.intent.extra.alarm.LENGTH"
private const val SEARCH_MODE = "android.intent.extra.alarm.SEARCH_MODE"
private const val IS_PM = "android.intent.extra.alarm.IS_PM"

class ClockIntentParserTest {
    private fun parse(action: String?, extras: Map<String, Any?> = emptyMap(), data: String? = null): ClockIntent =
        ClockIntentParser.parse(action, data, MapIntentExtras(extras))

    @Test
    fun actionStringsMatchPlatformValues() {
        assertThat(ClockIntentActions.SET_ALARM).isEqualTo("android.intent.action.SET_ALARM")
        assertThat(ClockIntentActions.SHOW_ALARMS).isEqualTo("android.intent.action.SHOW_ALARMS")
        assertThat(ClockIntentActions.SET_TIMER).isEqualTo("android.intent.action.SET_TIMER")
        assertThat(ClockIntentActions.SHOW_TIMERS).isEqualTo("android.intent.action.SHOW_TIMERS")
        assertThat(ClockIntentActions.DISMISS_TIMER).isEqualTo("android.intent.action.DISMISS_TIMER")
        assertThat(ClockIntentActions.DISMISS_ALARM).isEqualTo("android.intent.action.DISMISS_ALARM")
    }

    @Test
    fun setAlarmWithoutHourIsBlankEditorAndDropsEveryOtherExtra() {
        val result = parse(
            ClockIntentActions.SET_ALARM,
            mapOf(MESSAGE to "Gym", DAYS to arrayListOf(2), VIBRATE to true, SKIP_UI to true),
        )
        assertThat(result).isEqualTo(
            ClockIntent.SetAlarm(
                time = null,
                recurrence = Recurrence.OneTime,
                label = "",
                sound = AlarmSound.Default,
                vibrate = null,
                skipUi = false,
            ),
        )
    }

    @Test
    fun setAlarmMinutesAbsentMeansZero() {
        val result = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7))
        assertThat(result).isInstanceOf(ClockIntent.SetAlarm::class.java)
        assertThat((result as ClockIntent.SetAlarm).time).isEqualTo(LocalTime.of(7, 0))
    }

    @Test
    fun setAlarmAcceptsBoundaryHourAndMinute() {
        val result = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 23, MINUTES to 59)) as ClockIntent.SetAlarm
        assertThat(result.time).isEqualTo(LocalTime.of(23, 59))
    }

    @Test
    fun setAlarmHourOutOfRangeIsInvalid() {
        assertThat(parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 24)))
            .isEqualTo(ClockIntent.Invalid(ClockIntentActions.SET_ALARM))
        assertThat(parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to -1)))
            .isEqualTo(ClockIntent.Invalid(ClockIntentActions.SET_ALARM))
    }

    @Test
    fun setAlarmMinutesOutOfRangeIsInvalid() {
        assertThat(parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7, MINUTES to 60)))
            .isEqualTo(ClockIntent.Invalid(ClockIntentActions.SET_ALARM))
    }

    @Test
    fun setAlarmHourOfWrongTypeIsInvalid() {
        assertThat(parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to "7")))
            .isEqualTo(ClockIntent.Invalid(ClockIntentActions.SET_ALARM))
    }

    @Test
    fun setAlarmDaysArrayListMapsCalendarConstantsToDayOfWeek() {
        // Calendar.MONDAY = 2, Calendar.FRIDAY = 6
        val result = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7, DAYS to arrayListOf(2, 6)))
        assertThat((result as ClockIntent.SetAlarm).recurrence)
            .isEqualTo(Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
    }

    @Test
    fun setAlarmDaysIntArrayMapsSundayAndSaturday() {
        // Calendar.SUNDAY = 1, Calendar.SATURDAY = 7
        val result = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7, DAYS to intArrayOf(1, 7)))
        assertThat((result as ClockIntent.SetAlarm).recurrence)
            .isEqualTo(Recurrence.Weekly(setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)))
    }

    @Test
    fun setAlarmAllSevenDaysIsDaily() {
        val result = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7, DAYS to arrayListOf(1, 2, 3, 4, 5, 6, 7)))
        assertThat((result as ClockIntent.SetAlarm).recurrence).isEqualTo(Recurrence.Daily)
    }

    @Test
    fun setAlarmEmptyOrAbsentDaysIsOneTime() {
        val absent = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7)) as ClockIntent.SetAlarm
        val empty = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7, DAYS to arrayListOf<Int>())) as ClockIntent.SetAlarm
        val onlyUnknown = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7, DAYS to arrayListOf(0, 8))) as ClockIntent.SetAlarm
        assertThat(absent.recurrence).isEqualTo(Recurrence.OneTime)
        assertThat(empty.recurrence).isEqualTo(Recurrence.OneTime)
        assertThat(onlyUnknown.recurrence).isEqualTo(Recurrence.OneTime)
    }

    @Test
    fun setAlarmMessageIsTrimmedAndAbsentIsEmpty() {
        val trimmed = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7, MESSAGE to "  Gym  ")) as ClockIntent.SetAlarm
        val absent = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7)) as ClockIntent.SetAlarm
        assertThat(trimmed.label).isEqualTo("Gym")
        assertThat(absent.label).isEqualTo("")
    }

    @Test
    fun setAlarmRingtoneAbsentDefaultSilentAndUri() {
        val absent = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7)) as ClockIntent.SetAlarm
        val silent = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7, RINGTONE to "silent")) as ClockIntent.SetAlarm
        val uri = parse(
            ClockIntentActions.SET_ALARM,
            mapOf(HOUR to 7, RINGTONE to "content://media/external/audio/media/42"),
        ) as ClockIntent.SetAlarm
        assertThat(absent.sound).isEqualTo(AlarmSound.Default)
        assertThat(silent.sound).isEqualTo(AlarmSound.Silent)
        assertThat(uri.sound).isEqualTo(AlarmSound.Uri("content://media/external/audio/media/42"))
    }

    @Test
    fun setAlarmVibrateIsBooleanOrNull() {
        val on = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7, VIBRATE to true)) as ClockIntent.SetAlarm
        val off = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7, VIBRATE to false)) as ClockIntent.SetAlarm
        val absent = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7)) as ClockIntent.SetAlarm
        assertThat(on.vibrate).isTrue()
        assertThat(off.vibrate).isFalse()
        assertThat(absent.vibrate).isNull()
    }

    @Test
    fun setAlarmSkipUiDefaultsFalseAndHonoursTrue() {
        val absent = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7)) as ClockIntent.SetAlarm
        val set = parse(ClockIntentActions.SET_ALARM, mapOf(HOUR to 7, SKIP_UI to true)) as ClockIntent.SetAlarm
        assertThat(absent.skipUi).isFalse()
        assertThat(set.skipUi).isTrue()
    }

    @Test
    fun showAlarmsAndShowTimers() {
        assertThat(parse(ClockIntentActions.SHOW_ALARMS)).isEqualTo(ClockIntent.ShowAlarms)
        assertThat(parse(ClockIntentActions.SHOW_TIMERS)).isEqualTo(ClockIntent.ShowTimers)
    }

    @Test
    fun setTimerWithoutLengthOpensTimerTabAndIgnoresSkipUi() {
        val result = parse(ClockIntentActions.SET_TIMER, mapOf(MESSAGE to "Eggs", SKIP_UI to true))
        assertThat(result).isEqualTo(ClockIntent.SetTimer(length = null, label = "", skipUi = false))
    }

    @Test
    fun setTimerLengthIsSecondsLabelTrimmedAndSkipUi() {
        val result = parse(
            ClockIntentActions.SET_TIMER,
            mapOf(LENGTH to 90, MESSAGE to "  Eggs ", SKIP_UI to true),
        )
        assertThat(result).isEqualTo(ClockIntent.SetTimer(Duration.ofSeconds(90), "Eggs", skipUi = true))
    }

    @Test
    fun setTimerLengthOutsideOneSecondToOneDayIsInvalid() {
        assertThat(parse(ClockIntentActions.SET_TIMER, mapOf(LENGTH to 0)))
            .isEqualTo(ClockIntent.Invalid(ClockIntentActions.SET_TIMER))
        assertThat(parse(ClockIntentActions.SET_TIMER, mapOf(LENGTH to 86_401)))
            .isEqualTo(ClockIntent.Invalid(ClockIntentActions.SET_TIMER))
    }

    @Test
    fun setTimerLengthOfExactlyOneDayIsValid() {
        val result = parse(ClockIntentActions.SET_TIMER, mapOf(LENGTH to 86_400))
        assertThat(result).isEqualTo(ClockIntent.SetTimer(Duration.ofDays(1), "", skipUi = false))
    }

    @Test
    fun dismissTimerWithoutDataDismissesEveryRingingTimer() {
        assertThat(parse(ClockIntentActions.DISMISS_TIMER)).isEqualTo(ClockIntent.DismissTimer(timerId = null))
    }

    @Test
    fun dismissTimerTakesLastPathSegmentAsId() {
        val result = parse(ClockIntentActions.DISMISS_TIMER, data = "upyet://timer/42?x=1")
        assertThat(result).isEqualTo(ClockIntent.DismissTimer(timerId = 42L))
    }

    @Test
    fun dismissTimerDataWithoutNumericIdIsInvalidNotEveryTimer() {
        assertThat(parse(ClockIntentActions.DISMISS_TIMER, data = "upyet://timer/abc"))
            .isEqualTo(ClockIntent.Invalid(ClockIntentActions.DISMISS_TIMER))
    }

    @Test
    fun dismissAlarmWithoutSearchModeIsNone() {
        assertThat(parse(ClockIntentActions.DISMISS_ALARM)).isEqualTo(ClockIntent.DismissAlarm(AlarmSearch.None))
    }

    @Test
    fun dismissAlarmNextAndAllModes() {
        assertThat(parse(ClockIntentActions.DISMISS_ALARM, mapOf(SEARCH_MODE to "android.next")))
            .isEqualTo(ClockIntent.DismissAlarm(AlarmSearch.Next))
        assertThat(parse(ClockIntentActions.DISMISS_ALARM, mapOf(SEARCH_MODE to "android.all")))
            .isEqualTo(ClockIntent.DismissAlarm(AlarmSearch.All))
    }

    @Test
    fun dismissAlarmIgnoresDataUri() {
        val result = parse(ClockIntentActions.DISMISS_ALARM, data = "upyet://whatever/9")
        assertThat(result).isEqualTo(ClockIntent.DismissAlarm(AlarmSearch.None))
    }

    @Test
    fun dismissAlarmLabelIsTrimmedAndBlankIsInvalid() {
        assertThat(parse(ClockIntentActions.DISMISS_ALARM, mapOf(SEARCH_MODE to "android.label", MESSAGE to "  Wake  ")))
            .isEqualTo(ClockIntent.DismissAlarm(AlarmSearch.Label("Wake")))
        assertThat(parse(ClockIntentActions.DISMISS_ALARM, mapOf(SEARCH_MODE to "android.label", MESSAGE to "   ")))
            .isEqualTo(ClockIntent.Invalid(ClockIntentActions.DISMISS_ALARM))
    }

    @Test
    fun dismissAlarmTimeReadsHourMinutesAndIsPm() {
        val result = parse(
            ClockIntentActions.DISMISS_ALARM,
            mapOf(SEARCH_MODE to "android.time", HOUR to 7, MINUTES to 30, IS_PM to true),
        )
        assertThat(result).isEqualTo(ClockIntent.DismissAlarm(AlarmSearch.Time(hour = 7, minutes = 30, isPm = true)))
    }

    @Test
    fun dismissAlarmTimeIsPmAbsentStaysNull() {
        val result = parse(ClockIntentActions.DISMISS_ALARM, mapOf(SEARCH_MODE to "android.time", HOUR to 7))
        assertThat(result).isEqualTo(ClockIntent.DismissAlarm(AlarmSearch.Time(hour = 7, minutes = null, isPm = null)))
    }

    @Test
    fun dismissAlarmTimeWithNeitherHourNorMinutesIsInvalid() {
        assertThat(parse(ClockIntentActions.DISMISS_ALARM, mapOf(SEARCH_MODE to "android.time", IS_PM to true)))
            .isEqualTo(ClockIntent.Invalid(ClockIntentActions.DISMISS_ALARM))
    }

    @Test
    fun dismissAlarmTimeOutOfRangeIsInvalid() {
        assertThat(parse(ClockIntentActions.DISMISS_ALARM, mapOf(SEARCH_MODE to "android.time", HOUR to 24)))
            .isEqualTo(ClockIntent.Invalid(ClockIntentActions.DISMISS_ALARM))
        assertThat(parse(ClockIntentActions.DISMISS_ALARM, mapOf(SEARCH_MODE to "android.time", MINUTES to 60)))
            .isEqualTo(ClockIntent.Invalid(ClockIntentActions.DISMISS_ALARM))
    }

    @Test
    fun dismissAlarmUnknownModeIsInvalid() {
        assertThat(parse(ClockIntentActions.DISMISS_ALARM, mapOf(SEARCH_MODE to "android.bogus")))
            .isEqualTo(ClockIntent.Invalid(ClockIntentActions.DISMISS_ALARM))
    }

    @Test
    fun nullOrUnservedActionIsUnsupported() {
        assertThat(parse(null)).isEqualTo(ClockIntent.Unsupported)
        assertThat(parse("android.intent.action.VIEW")).isEqualTo(ClockIntent.Unsupported)
        assertThat(parse("android.intent.action.SNOOZE_ALARM")).isEqualTo(ClockIntent.Unsupported)
    }
}
