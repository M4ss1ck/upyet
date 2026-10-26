package dev.upyet.core.directboot

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.upyet.timer.domain.TimerId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * The timer mirror is the only thing that knows about a ringing timer before first unlock, and it is a
 * positional `|` string in device-protected SharedPreferences rather than a schema anything validates.
 * Every field appended to it is a compatibility question that only real storage can answer, and the
 * label and URI encoding depends on what the platform's SharedPreferences actually persists, which is
 * why these run on a device rather than as JVM tests over a fake.
 */
@RunWith(AndroidJUnit4::class)
class DirectBootTimerStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val unlockState = MutableUnlockState()
    private val store = DirectBootTimerStore(context, unlockState)
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("timer_mirror", Context.MODE_PRIVATE)

    @Before fun clean() = store.clear()

    @After fun cleanUp() = store.clear()

    @Test fun aRunningTimerRoundTripsEveryField() {
        val record = MirroredTimer(
            timerId = TimerId(7),
            state = MirroredTimerState.RUNNING,
            length = Duration.ofMinutes(10),
            remainingAtAnchor = Duration.ofSeconds(425),
            wallClockAnchor = Instant.parse("2026-10-26T08:00:00Z"),
            endsAt = Instant.parse("2026-10-26T08:07:05Z"),
            label = "Pasta | eggs: 100% ñ",
            ringtoneUri = "content://media/internal/audio/media/42",
            evidenceEnabled = true,
        )
        store.put(record)

        assertThat(store.all().single()).isEqualTo(record.copy(writtenWhileLocked = false))
    }

    @Test fun everyStateRoundTrips() {
        val records = setOf(
            MirroredTimer(
                timerId = TimerId(1),
                state = MirroredTimerState.IDLE,
                length = Duration.ofMinutes(5),
                remainingAtAnchor = null,
                wallClockAnchor = null,
                endsAt = null,
                label = "Idle",
                ringtoneUri = null,
                evidenceEnabled = false,
            ),
            MirroredTimer(
                timerId = TimerId(2),
                state = MirroredTimerState.PAUSED,
                length = Duration.ofMinutes(10),
                remainingAtAnchor = Duration.ofSeconds(30),
                wallClockAnchor = null,
                endsAt = null,
                label = "Tea",
                ringtoneUri = null,
                evidenceEnabled = true,
            ),
            MirroredTimer(
                timerId = TimerId(3),
                state = MirroredTimerState.RINGING,
                length = Duration.ofMinutes(3),
                remainingAtAnchor = null,
                wallClockAnchor = null,
                endsAt = null,
                label = "Eggs",
                ringtoneUri = "content://ring",
                evidenceEnabled = true,
            ),
            MirroredTimer(
                timerId = TimerId(4),
                state = MirroredTimerState.MISSED,
                length = Duration.ofMinutes(1),
                remainingAtAnchor = null,
                wallClockAnchor = null,
                endsAt = null,
                label = "",
                ringtoneUri = null,
                evidenceEnabled = false,
            ),
        )
        records.forEach(store::put)

        assertThat(store.all().toSet()).isEqualTo(records.map { it.copy(writtenWhileLocked = false) }.toSet())
    }

    @Test fun aRecordWrittenWhileLockedIsMarked() {
        val record = pausedRecord()

        unlockState.unlocked = false
        store.put(record)
        assertThat(store.all().single().writtenWhileLocked).isTrue()

        // A write after unlock does not clear it: only reconciliation, once Room has the run state, does.
        unlockState.unlocked = true
        store.put(record)
        assertThat(store.all().single().writtenWhileLocked).isTrue()

        store.markReconciled(record.timerId)
        assertThat(store.all().single()).isEqualTo(record)
    }

    @Test fun markingAnAbsentTimerReconciledWritesNothing() {
        store.markReconciled(TimerId(42))

        assertThat(store.all()).isEmpty()
    }

    @Test fun theCallerCannotForgeTheLockedFlag() {
        store.put(pausedRecord().copy(writtenWhileLocked = true))

        assertThat(store.all().single().writtenWhileLocked).isFalse()
    }

    /**
     * A record written by a build that predates the trailing `writtenWhileLocked` field has eight fields,
     * not nine. It must still parse, or an app update would silently drop every ringing-capable timer
     * mirrored before it.
     */
    @Test fun aRecordWrittenBeforeTheLastFieldExistedStillParses() {
        preferences.edit().putString("timer:3", "PAUSED|600000|30000|||Tea||false").commit()

        assertThat(store.all().single()).isEqualTo(
            MirroredTimer(
                timerId = TimerId(3),
                state = MirroredTimerState.PAUSED,
                length = Duration.ofMinutes(10),
                remainingAtAnchor = Duration.ofSeconds(30),
                wallClockAnchor = null,
                endsAt = null,
                label = "Tea",
                ringtoneUri = null,
                evidenceEnabled = false,
                writtenWhileLocked = false,
            ),
        )
    }

    @Test fun aMalformedRecordDoesNotHideTheOthers() {
        preferences.edit().putString("timer:8", "garbage").commit()
        store.put(pausedRecord(id = 1))

        assertThat(store.all().map { it.timerId }).containsExactly(TimerId(1))
    }

    @Test fun removeDeletesOnlyThatTimer() {
        store.put(pausedRecord(id = 1))
        store.put(pausedRecord(id = 2))

        store.remove(TimerId(1))

        assertThat(store.all().map { it.timerId }).containsExactly(TimerId(2))
    }

    /** Each mirror is its own file, so clearing or reading one can never drop or misread the other's records. */
    @Test fun theTimerMirrorAndTheAlarmMirrorAreSeparateFiles() {
        val alarmPreferences = context.createDeviceProtectedStorageContext()
            .getSharedPreferences("alarm_mirror", Context.MODE_PRIVATE)
        val alarmKeysBefore = alarmPreferences.all.keys.toSet()

        store.put(pausedRecord())
        store.clear()
        store.put(pausedRecord(id = 6))

        assertThat(alarmPreferences.all.keys).isEqualTo(alarmKeysBefore)
        assertThat(store.all().map { it.timerId }).containsExactly(TimerId(6))
    }

    private fun pausedRecord(id: Long = 5) = MirroredTimer(
        timerId = TimerId(id),
        state = MirroredTimerState.PAUSED,
        length = Duration.ofMinutes(10),
        remainingAtAnchor = Duration.ofSeconds(30),
        wallClockAnchor = null,
        endsAt = null,
        label = "Tea",
        ringtoneUri = null,
        evidenceEnabled = false,
    )

    private class MutableUnlockState(var unlocked: Boolean = true) : UserUnlockState {
        override fun isUserUnlocked(): Boolean = unlocked
    }
}
