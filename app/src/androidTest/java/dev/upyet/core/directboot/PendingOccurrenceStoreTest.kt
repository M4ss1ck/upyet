package dev.upyet.core.directboot

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.evidence.domain.TimerAttribution
import dev.upyet.timer.domain.TimerId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The pending store serves both the alarm and the timer path before first unlock, as positional `|` strings in
 * device-protected SharedPreferences (ADR 0010). A record already on disk from an older build must still read as an
 * alarm, which only real storage can show.
 */
@RunWith(AndroidJUnit4::class)
class PendingOccurrenceStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = PendingOccurrenceStore(context)
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("pending_occurrences", Context.MODE_PRIVATE)
    private val at = Instant.parse("2026-10-27T06:00:00Z")

    @Before fun clean() = store.clear()

    @After fun cleanUp() = store.clear()

    @Test fun alarmAndTimerRecordsRoundTripSideBySide() {
        val alarm = PendingOccurrence(AlarmId(7), at, at.plusSeconds(1), null, "RINGING")
        val timer = PendingTimerOccurrence(listOf(TimerAttribution(TimerId(7), "Eggs | soft")), at.plusSeconds(2), null, "RINGING")

        store.append(alarm)
        store.append(timer)

        assertThat(store.all()).containsExactly(alarm, timer)
    }

    @Test fun appendKeepsOneRecordPerIdentity() {
        val ringing = PendingTimerOccurrence(listOf(TimerAttribution(TimerId(1), "Tea")), at, null, "RINGING")
        val stopped = ringing.copy(dismissedAt = at.plusSeconds(30), outcome = "STOPPED")

        store.append(ringing)
        store.append(stopped)

        assertThat(store.all()).containsExactly(stopped)
    }

    @Test fun replaceIfPendingWritesNothingOnceTheStoreWasCleared() {
        val ringing = PendingTimerOccurrence(listOf(TimerAttribution(TimerId(1), "Tea")), at, null, "RINGING")
        store.append(ringing)
        store.clear()

        val written = store.replaceIfPending(ringing.copy(outcome = "STOPPED"))

        assertThat(written).isFalse()
        assertThat(store.all()).isEmpty()
    }

    @Test fun replaceIfPendingUpdatesAWaitingRecord() {
        val ringing = PendingTimerOccurrence(listOf(TimerAttribution(TimerId(1), "Tea")), at, null, "RINGING")
        store.append(ringing)
        val stopped = ringing.copy(dismissedAt = at.plusSeconds(5), outcome = "STOPPED")

        assertThat(store.replaceIfPending(stopped)).isTrue()
        assertThat(store.all()).containsExactly(stopped)
    }

    @Test fun aLegacyRecordAlreadyOnDiskReadsAsAnAlarm() {
        preferences.edit { putStringSet("records", setOf("42|${at.toEpochMilli()}|${at.toEpochMilli() + 5_000}|-1|RINGING")) }

        assertThat(store.all()).containsExactly(PendingOccurrence(AlarmId(42), at, at.plusSeconds(5), null, "RINGING"))
    }
}
