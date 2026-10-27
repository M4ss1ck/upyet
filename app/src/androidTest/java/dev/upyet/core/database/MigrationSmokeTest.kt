package dev.upyet.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationSmokeTest {
    @get:Rule val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            UpYetDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    @Throws(IOException::class)
    fun opensVersionOne() {
        helper.createDatabase("migration-test", 1).close()
    }

    @Test
    @Throws(IOException::class)
    fun migratesFrom1To2AddsSkipColumnAndPreservesData() {
        // Create a v1 database with one alarm and no skip column.
        helper.createDatabase("migration-test-1-2", 1).apply {
            execSQL(
                "INSERT INTO alarms (id, minuteOfDay, enabled, label, recurrenceType, weekdayMask, soundUri, " +
                    "vibrationEnabled, snoozeMinutes, evidenceEnabled, createdAt, updatedAt) " +
                    "VALUES (1, 450, 1, 'Wake', 'DAILY', 0, NULL, 1, 9, 0, 100, 200)",
            )
            close()
        }

        // Run the 1 -> 2 migration and validate the schema.
        val db = helper.runMigrationsAndValidate("migration-test-1-2", 3, true, *UpYetDatabase.MIGRATIONS)
        // The new column must exist and be NULL for the pre-existing row.
        db.query("SELECT skipNextOnEpochDay, label FROM alarms WHERE id = 1").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.isNull(0))
            assert(cursor.getString(1) == "Wake")
        }
        // New rows can write a non-null skip date.
        db.execSQL("UPDATE alarms SET skipNextOnEpochDay = 20123 WHERE id = 1")
        db.query("SELECT skipNextOnEpochDay FROM alarms WHERE id = 1").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getLong(0) == 20123L)
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migratesFrom2To3AddsSoundEnabledColumnAndDefaultsToTrue() {
        // Create a v2 database with one alarm and no soundEnabled column.
        helper.createDatabase("migration-test-2-3", 2).apply {
            execSQL(
                "INSERT INTO alarms (id, minuteOfDay, enabled, label, recurrenceType, weekdayMask, soundUri, " +
                    "vibrationEnabled, snoozeMinutes, evidenceEnabled, skipNextOnEpochDay, createdAt, updatedAt) " +
                    "VALUES (1, 450, 1, 'Wake', 'DAILY', 0, 'content://ring', 1, 9, 0, 20123, 100, 200)",
            )
            close()
        }

        // Run the 2 -> 3 migration and validate the schema.
        val db = helper.runMigrationsAndValidate("migration-test-2-3", 3, true, *UpYetDatabase.MIGRATIONS)
        // The new column must exist and be 1 (true) for the pre-existing row, preserving the ringtone.
        db.query("SELECT soundEnabled, label, soundUri FROM alarms WHERE id = 1").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getInt(0) == 1)
            assert(cursor.getString(1) == "Wake")
            assert(cursor.getString(2) == "content://ring")
        }
        // New rows can write soundEnabled = 0 (silent) while keeping soundUri.
        db.execSQL("UPDATE alarms SET soundEnabled = 0 WHERE id = 1")
        db.query("SELECT soundEnabled, soundUri FROM alarms WHERE id = 1").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getInt(0) == 0)
            assert(cursor.getString(1) == "content://ring")
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun openMigratedDatabaseWithRoom() {
        helper.createDatabase("migration-test-room", 1).close()
        helper.runMigrationsAndValidate("migration-test-room", 3, true, *UpYetDatabase.MIGRATIONS).close()

        // Opening via Room after migration must succeed and expose the new DAO method.
        val roomDb = androidx.room.Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            UpYetDatabase::class.java,
            "migration-test-room",
        ).addMigrations(*UpYetDatabase.MIGRATIONS).build()
        roomDb.openHelper.writableDatabase.query("SELECT skipNextOnEpochDay FROM alarms").use { cursor ->
            // Table exists and is readable; no rows expected beyond the empty migrated db.
            assert(cursor.count == 0)
        }
        roomDb.close()
    }

    @Test
    @Throws(IOException::class)
    fun migratesFrom3To4AddsAnEmptyTimersTableAndKeepsAlarms() {
        // Create a v3 database with one alarm, including the soundEnabled column that v3 introduced.
        helper.createDatabase("migration-test-3-4", 3).apply {
            execSQL(
                "INSERT INTO alarms (id, minuteOfDay, enabled, label, recurrenceType, weekdayMask, soundUri, " +
                    "soundEnabled, vibrationEnabled, snoozeMinutes, evidenceEnabled, skipNextOnEpochDay, createdAt, updatedAt) " +
                    "VALUES (1, 450, 1, 'Wake', 'DAILY', 0, 'content://ring', 1, 1, 9, 0, 20123, 100, 200)",
            )
            close()
        }

        // Run the 3 -> 4 migration and validate the schema.
        val db = helper.runMigrationsAndValidate("migration-test-3-4", 4, true, *UpYetDatabase.MIGRATIONS)
        // The alarm must survive untouched.
        db.query("SELECT label FROM alarms WHERE id = 1").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getString(0) == "Wake")
        }
        // The new table starts empty.
        db.query("SELECT COUNT(*) FROM timers").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getInt(0) == 0)
        }
        // A running timer row with every column written must be readable back.
        db.execSQL(
            "INSERT INTO timers (id, lengthMillis, label, ringtoneUri, evidenceEnabled, runState, " +
                "remainingAtAnchorMillis, anchorElapsedRealtimeMillis, anchorWallClockMillis, createdAt) " +
                "VALUES (1, 600000, 'Pasta', NULL, 1, 'RUNNING', 425000, 123456, 1793001600000, 100)",
        )
        db.query("SELECT label, runState FROM timers WHERE id = 1").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getString(0) == "Pasta")
            assert(cursor.getString(1) == "RUNNING")
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migratesFrom1To4() {
        // Create a v1 database with one alarm and none of the later columns.
        helper.createDatabase("migration-test-1-4", 1).apply {
            execSQL(
                "INSERT INTO alarms (id, minuteOfDay, enabled, label, recurrenceType, weekdayMask, soundUri, " +
                    "vibrationEnabled, snoozeMinutes, evidenceEnabled, createdAt, updatedAt) " +
                    "VALUES (1, 450, 1, 'Wake', 'DAILY', 0, NULL, 1, 9, 0, 100, 200)",
            )
            close()
        }

        // Run every migration from 1 to 4 and validate the schema.
        val db = helper.runMigrationsAndValidate("migration-test-1-4", 4, true, *UpYetDatabase.MIGRATIONS)
        // The alarm must survive the whole chain.
        db.query("SELECT label FROM alarms WHERE id = 1").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getString(0) == "Wake")
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migratesFrom4To5AddsSourceAndKeepsOccurrences() {
        helper.createDatabase("migration-test-4-5", 4).apply {
            execSQL(
                "INSERT INTO alarms (id, minuteOfDay, enabled, label, recurrenceType, weekdayMask, soundUri, " +
                    "soundEnabled, vibrationEnabled, snoozeMinutes, evidenceEnabled, skipNextOnEpochDay, createdAt, updatedAt) " +
                    "VALUES (1, 450, 1, 'Wake', 'DAILY', 0, 'content://ring', 1, 1, 9, 0, 20123, 100, 200)",
            )
            execSQL(
                "INSERT INTO alarm_occurrences (id, alarmId, scheduledFor, triggeredAt, activityVisibleAt, dismissedAt, " +
                    "outcome, parentOccurrenceId) VALUES (7, 1, 1000, 1001, 1002, 1003, 'DISMISSED', NULL)",
            )
            execSQL(
                "INSERT INTO evidence_segments (id, occurrenceId, requestedAt, startedAt, endedAt, finalizedAt, fileName, " +
                    "durationMs, sizeBytes, status, errorCode) VALUES (3, 7, 1000, 1001, 1002, 1003, 'clip.mp4', 900, 4096, 'RECORDED', NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate("migration-test-4-5", 5, true, *UpYetDatabase.MIGRATIONS)
        // Every original column of the existing occurrence reads back unchanged.
        db.query(
            "SELECT id, alarmId, scheduledFor, triggeredAt, activityVisibleAt, dismissedAt, outcome, parentOccurrenceId, " +
                "source, timerAttribution FROM alarm_occurrences WHERE id = 7",
        ).use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getLong(0) == 7L)
            assert(cursor.getLong(1) == 1L)
            assert(cursor.getLong(2) == 1000L)
            assert(cursor.getLong(3) == 1001L)
            assert(cursor.getLong(4) == 1002L)
            assert(cursor.getLong(5) == 1003L)
            assert(cursor.getString(6) == "DISMISSED")
            assert(cursor.isNull(7))
            assert(cursor.getString(8) == "ALARM")
            assert(cursor.isNull(9))
        }
        // The occurrence keeps its evidence through the migration.
        db.query("SELECT fileName, status FROM evidence_segments WHERE occurrenceId = 7").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getString(0) == "clip.mp4")
            assert(cursor.getString(1) == "RECORDED")
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migratesFrom5To6AddsVoiceDefaultingToFalseAndKeepsAlarms() {
        helper.createDatabase("migration-test-5-6", 5).apply {
            execSQL(
                "INSERT INTO alarms (id, minuteOfDay, enabled, label, recurrenceType, weekdayMask, soundUri, " +
                    "soundEnabled, vibrationEnabled, snoozeMinutes, evidenceEnabled, skipNextOnEpochDay, createdAt, updatedAt) " +
                    "VALUES (1, 450, 1, 'Wake', 'ONE_TIME', 0, 'content://ring', 1, 1, 9, 0, NULL, 100, 200)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate("migration-test-5-6", 6, true, *UpYetDatabase.MIGRATIONS)
        // An alarm that existed before voice alarms was made by the user, so it must read back as not voice.
        db.query("SELECT voice, label, soundUri FROM alarms WHERE id = 1").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getInt(0) == 0)
            assert(cursor.getString(1) == "Wake")
            assert(cursor.getString(2) == "content://ring")
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migratesFrom6To7KeepsSnoozeValuesAcceptsNullAndKeepsIdSequence() {
        helper.createDatabase("migration-test-6-7", 6).apply {
            execSQL(
                "INSERT INTO alarms (id, minuteOfDay, enabled, label, recurrenceType, weekdayMask, soundUri, " +
                    "soundEnabled, vibrationEnabled, snoozeMinutes, evidenceEnabled, skipNextOnEpochDay, voice, createdAt, updatedAt) " +
                    "VALUES (1, 450, 1, 'Wake', 'DAILY', 0, NULL, 1, 1, 10, 0, NULL, 0, 100, 200)",
            )
            // Id 2 is the newest alarm and is deleted before the migration; AUTOINCREMENT must never reissue it.
            execSQL(
                "INSERT INTO alarms (id, minuteOfDay, enabled, label, recurrenceType, weekdayMask, soundUri, " +
                    "soundEnabled, vibrationEnabled, snoozeMinutes, evidenceEnabled, skipNextOnEpochDay, voice, createdAt, updatedAt) " +
                    "VALUES (2, 300, 1, 'Gone', 'ONE_TIME', 0, NULL, 1, 1, 9, 0, NULL, 0, 100, 200)",
            )
            execSQL("DELETE FROM alarms WHERE id = 2")
            close()
        }

        val db = helper.runMigrationsAndValidate("migration-test-6-7", 7, true, *UpYetDatabase.MIGRATIONS)
        // An existing snooze value survives the rebuild unchanged.
        db.query("SELECT snoozeMinutes, label FROM alarms WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(10)
            assertThat(cursor.getString(1)).isEqualTo("Wake")
        }
        // NULL means snooze Off, so the rebuilt column must accept it.
        db.execSQL("UPDATE alarms SET snoozeMinutes = NULL WHERE id = 1")
        db.query("SELECT snoozeMinutes FROM alarms WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.isNull(0)).isTrue()
        }
        // The next alarm continues after the deleted id 2 instead of reusing it.
        db.execSQL(
            "INSERT INTO alarms (minuteOfDay, enabled, label, recurrenceType, weekdayMask, soundUri, " +
                "soundEnabled, vibrationEnabled, snoozeMinutes, evidenceEnabled, skipNextOnEpochDay, voice, createdAt, updatedAt) " +
                "VALUES (480, 1, 'Next', 'ONE_TIME', 0, NULL, 1, 1, NULL, 0, NULL, 0, 300, 300)",
        )
        db.query("SELECT id FROM alarms WHERE label = 'Next'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(3L)
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migratesFrom1To5() {
        helper.createDatabase("migration-test-1-5", 1).apply {
            execSQL(
                "INSERT INTO alarms (id, minuteOfDay, enabled, label, recurrenceType, weekdayMask, soundUri, " +
                    "vibrationEnabled, snoozeMinutes, evidenceEnabled, createdAt, updatedAt) " +
                    "VALUES (1, 450, 1, 'Wake', 'DAILY', 0, NULL, 1, 9, 0, 100, 200)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate("migration-test-1-5", 5, true, *UpYetDatabase.MIGRATIONS)
        db.query("SELECT label FROM alarms WHERE id = 1").use { cursor ->
            assert(cursor.moveToFirst())
            assert(cursor.getString(0) == "Wake")
        }
        db.close()
    }
}
