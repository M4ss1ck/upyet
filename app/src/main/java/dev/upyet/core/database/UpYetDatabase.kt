package dev.upyet.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class, AlarmOccurrenceEntity::class, EvidenceSegmentEntity::class, TimerEntity::class],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class UpYetDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    abstract fun occurrenceDao(): OccurrenceDao

    abstract fun evidenceSegmentDao(): EvidenceSegmentDao

    abstract fun timerDao(): TimerDao

    companion object {
        /** Exactly the `createSql` of schema 4's `timers` entity, so `runMigrationsAndValidate` passes. */
        private const val CREATE_TIMERS =
            "CREATE TABLE IF NOT EXISTS `timers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`lengthMillis` INTEGER NOT NULL, `label` TEXT NOT NULL, `ringtoneUri` TEXT, `evidenceEnabled` INTEGER NOT NULL, " +
                "`runState` TEXT NOT NULL, `remainingAtAnchorMillis` INTEGER, `anchorElapsedRealtimeMillis` INTEGER, " +
                "`anchorWallClockMillis` INTEGER, `createdAt` INTEGER NOT NULL)"

        /** Destructive migration is forbidden; add explicit migrations for schema changes. */
        val MIGRATIONS: Array<Migration> = arrayOf(
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE alarms ADD COLUMN skipNextOnEpochDay INTEGER")
                }
            },
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE alarms ADD COLUMN soundEnabled INTEGER NOT NULL DEFAULT 1")
                }
            },
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // A new table only: no existing row is read or rewritten.
                    db.execSQL(CREATE_TIMERS)
                }
            },
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Two added columns, no existing row read or rewritten. Every existing row reads back as an
                    // alarm occurrence because the source defaults to ALARM and the attribution stays NULL.
                    db.execSQL("ALTER TABLE alarm_occurrences ADD COLUMN source TEXT NOT NULL DEFAULT 'ALARM'")
                    db.execSQL("ALTER TABLE alarm_occurrences ADD COLUMN timerAttribution TEXT")
                }
            },
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Existing alarms were never voice alarms, so the default of 0 is the truth for every row.
                    db.execSQL("ALTER TABLE alarms ADD COLUMN voice INTEGER NOT NULL DEFAULT 0")
                }
            },
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // SQLite cannot drop NOT NULL in place, so rebuild the table. Column order, types and defaults
                    // match schema 7 exactly; only snoozeMinutes loses NOT NULL, because NULL now means snooze Off.
                    // alarms has no inbound foreign keys, so DROP TABLE cascades nothing.
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `alarms_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`minuteOfDay` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
                            "`recurrenceType` TEXT NOT NULL, `weekdayMask` INTEGER NOT NULL, `soundUri` TEXT, " +
                            "`soundEnabled` INTEGER NOT NULL DEFAULT 1, `vibrationEnabled` INTEGER NOT NULL, " +
                            "`snoozeMinutes` INTEGER, `evidenceEnabled` INTEGER NOT NULL, `skipNextOnEpochDay` INTEGER, " +
                            "`voice` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
                    )
                    // Carry AUTOINCREMENT's high-water mark across, before the old table (and its sqlite_sequence
                    // row) is dropped, so a deleted newest alarm's id is never handed to a new alarm.
                    db.execSQL(
                        "INSERT INTO sqlite_sequence (name, seq) SELECT 'alarms_new', seq FROM sqlite_sequence " +
                            "WHERE name = 'alarms'",
                    )
                    db.execSQL(
                        "INSERT INTO alarms_new (id, minuteOfDay, enabled, label, recurrenceType, weekdayMask, soundUri, " +
                            "soundEnabled, vibrationEnabled, snoozeMinutes, evidenceEnabled, skipNextOnEpochDay, voice, " +
                            "createdAt, updatedAt) " +
                            "SELECT id, minuteOfDay, enabled, label, recurrenceType, weekdayMask, soundUri, " +
                            "soundEnabled, vibrationEnabled, snoozeMinutes, evidenceEnabled, skipNextOnEpochDay, voice, " +
                            "createdAt, updatedAt FROM alarms",
                    )
                    db.execSQL("DROP TABLE alarms")
                    db.execSQL("ALTER TABLE alarms_new RENAME TO alarms")
                }
            },
        )
    }
}
