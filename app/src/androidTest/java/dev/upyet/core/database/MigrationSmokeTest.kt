package dev.upyet.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
}
