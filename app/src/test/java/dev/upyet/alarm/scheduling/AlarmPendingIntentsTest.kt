package dev.upyet.alarm.scheduling

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import org.junit.Test

class AlarmPendingIntentsTest {
    @Test
    fun requestCodesAreStableAndCollisionSafeForSupportedIds() {
        val main = AlarmPendingIntents.requestCode(AlarmId(12), AlarmOccurrenceKind.MAIN)
        val snooze = AlarmPendingIntents.requestCode(AlarmId(12), AlarmOccurrenceKind.SNOOZE)
        assertThat(main).isNotEqualTo(snooze)
        assertThat(main).isNotEqualTo(AlarmPendingIntents.requestCode(AlarmId(13), AlarmOccurrenceKind.MAIN))
        assertThat(main).isEqualTo(AlarmPendingIntents.requestCode(AlarmId(12), AlarmOccurrenceKind.MAIN))
    }
}
