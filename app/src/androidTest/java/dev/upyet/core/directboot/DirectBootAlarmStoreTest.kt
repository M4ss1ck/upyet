package dev.upyet.core.directboot

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.SnoozeBudget
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The mirror is the only thing that knows about a pending alarm before first unlock, and it is a
 * positional `|` string in device-protected SharedPreferences rather than a schema anything validates.
 * Every field appended to it is therefore a compatibility question that only real storage can answer,
 * which is why these run on a device rather than as JVM tests over a fake.
 */
@RunWith(AndroidJUnit4::class)
class DirectBootAlarmStoreTest {
    private val store = DirectBootAlarmStore(ApplicationProvider.getApplicationContext())
    private val preferences = ApplicationProvider.getApplicationContext<android.content.Context>()
        .createDeviceProtectedStorageContext()
        .getSharedPreferences("alarm_mirror", android.content.Context.MODE_PRIVATE)

    @Before fun clean() = store.clear()

    @After fun cleanUp() = store.clear()

    @Test fun aSnoozeRecordRoundTripsItsBudgetAndChainStart() {
        val chainStart = Instant.parse("2026-08-24T06:00:00Z")
        store.put(record(kind = AlarmOccurrenceKind.SNOOZE, snoozesRemaining = 2, chainStartedAtMillis = chainStart.toEpochMilli()))

        val restored = store.all().single()

        assertThat(restored.snoozesRemaining).isEqualTo(2)
        assertThat(restored.chainStartedAtMillis).isEqualTo(chainStart.toEpochMilli())
        assertThat(restored.kind).isEqualTo(AlarmOccurrenceKind.SNOOZE)
    }

    @Test fun anUnlimitedBudgetSurvivesTheRoundTrip() {
        store.put(record(snoozesRemaining = SnoozeBudget.UNLIMITED))

        assertThat(SnoozeBudget(store.all().single().snoozesRemaining).isUnlimited).isTrue()
    }

    /**
     * A record written by a build that predates the budget has seven fields, not nine. It must still
     * parse, or an app update would silently drop every alarm scheduled before it - the exact failure
     * the mirror exists to prevent.
     */
    @Test fun aRecordWrittenBeforeTheBudgetExistedStillParses() {
        val triggerAt = Instant.parse("2026-08-24T07:30:00Z")
        preferences.edit().putString(
            "alarm:7:MAIN",
            listOf(triggerAt.toEpochMilli(), 9, true, "", 450, "WEEKLY", 0b0011111).joinToString("|"),
        ).commit()

        val restored = store.all().single()

        assertThat(restored.alarmId).isEqualTo(AlarmId(7))
        assertThat(restored.triggerAt).isEqualTo(triggerAt)
        assertThat(restored.snoozeMinutes).isEqualTo(9)
        assertThat(restored.recurrenceType).isEqualTo("WEEKLY")
        // Absent, not zero: an old record must not read as a chain with no snoozes left.
        assertThat(restored.snoozesRemaining).isEqualTo(SnoozeBudget.UNSET)
        assertThat(SnoozeBudget.of(restored.snoozesRemaining).remaining).isEqualTo(SnoozeBudget.DEFAULT_MAX)
        assertThat(restored.chainStartedAtMillis).isEqualTo(0L)
    }

    @Test fun aMainAndASnoozeForOneAlarmAreStoredSeparately() {
        store.put(record(kind = AlarmOccurrenceKind.MAIN, snoozesRemaining = SnoozeBudget.UNSET))
        store.put(record(kind = AlarmOccurrenceKind.SNOOZE, snoozesRemaining = 1))

        val restored = store.all().associateBy { it.kind }

        assertThat(restored.keys).containsExactly(AlarmOccurrenceKind.MAIN, AlarmOccurrenceKind.SNOOZE)
        assertThat(restored.getValue(AlarmOccurrenceKind.SNOOZE).snoozesRemaining).isEqualTo(1)
    }

    @Test fun removingOneKindLeavesTheOther() {
        store.put(record(kind = AlarmOccurrenceKind.MAIN))
        store.put(record(kind = AlarmOccurrenceKind.SNOOZE))

        store.remove(AlarmId(1), AlarmOccurrenceKind.SNOOZE)

        assertThat(store.all().single().kind).isEqualTo(AlarmOccurrenceKind.MAIN)
    }

    @Test fun soundDisabledWithRingtoneSurvivesRoundTrip() {
        val ringtone = "content://media/internal/audio/media/42"
        store.put(
            MirroredAlarm(
                alarmId = AlarmId(1),
                kind = AlarmOccurrenceKind.MAIN,
                triggerAt = Instant.parse("2026-08-24T07:00:00Z"),
                snoozeMinutes = 9,
                vibrationEnabled = true,
                soundUri = ringtone,
                minuteOfDay = 420,
                recurrenceType = "DAILY",
                weekdayMask = 0,
                snoozesRemaining = SnoozeBudget.DEFAULT_MAX,
                chainStartedAtMillis = 0L,
                skipNextOnEpochDay = null,
                soundEnabled = false,
            ),
        )

        val restored = store.all().single()

        assertThat(restored.soundEnabled).isFalse()
        assertThat(restored.soundUri).isEqualTo(ringtone)
    }

    @Test fun aRecordWrittenBeforeSoundEnabledStillParsesAsEnabled() {
        val triggerAt = Instant.parse("2026-08-24T07:30:00Z")
        // 10 fields is the format before soundEnabled was added (9..10 includes skipNextOnEpochDay, but no soundEnabled).
        preferences.edit().putString(
            "alarm:7:MAIN",
            listOf(
                triggerAt.toEpochMilli(),
                9,
                true,
                "content://ring",
                450,
                "WEEKLY",
                0b0011111,
                SnoozeBudget.UNSET,
                0L,
                "",
            ).joinToString("|"),
        ).commit()

        val restored = store.all().single()

        assertThat(restored.alarmId).isEqualTo(AlarmId(7))
        assertThat(restored.triggerAt).isEqualTo(triggerAt)
        assertThat(restored.soundUri).isEqualTo("content://ring")
        assertThat(restored.soundEnabled).isTrue()
        assertThat(restored.skipNextOnEpochDay).isNull()
    }

    private fun record(
        kind: AlarmOccurrenceKind = AlarmOccurrenceKind.MAIN,
        snoozesRemaining: Int = SnoozeBudget.DEFAULT_MAX,
        chainStartedAtMillis: Long = 0L,
    ) = MirroredAlarm(
        alarmId = AlarmId(1),
        kind = kind,
        triggerAt = Instant.parse("2026-08-24T07:00:00Z"),
        snoozeMinutes = 9,
        vibrationEnabled = true,
        soundUri = null,
        minuteOfDay = 420,
        recurrenceType = "DAILY",
        weekdayMask = 0,
        snoozesRemaining = snoozesRemaining,
        chainStartedAtMillis = chainStartedAtMillis,
    )
}
