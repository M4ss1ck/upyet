package dev.upyet.core.directboot

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.SnoozePolicy
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import org.junit.Test
import java.time.Instant

class MirroredAlarmMapperTest {
    private fun mirrored(snoozeMinutes: Int) = MirroredAlarm(
        alarmId = AlarmId(7),
        kind = AlarmOccurrenceKind.MAIN,
        triggerAt = Instant.parse("2026-10-27T07:00:00Z"),
        snoozeMinutes = snoozeMinutes,
        vibrationEnabled = false,
        soundUri = null,
        minuteOfDay = 7 * 60,
        recurrenceType = MirroredRecurrence.ONE_TIME,
        weekdayMask = 0,
        snoozesRemaining = 3,
        chainStartedAtMillis = 0L,
    )

    @Test
    fun zeroInTheMirrorIsSnoozeOff() {
        assertThat(mirrored(snoozeMinutes = 0).toAlarm().snooze).isEqualTo(SnoozePolicy.Off)
    }

    @Test
    fun positiveMinutesInTheMirrorAreAnAfterPolicy() {
        assertThat(mirrored(snoozeMinutes = 9).toAlarm().snooze).isEqualTo(SnoozePolicy.After(9))
    }
}
