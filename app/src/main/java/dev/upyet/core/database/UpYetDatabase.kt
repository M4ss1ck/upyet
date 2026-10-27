package dev.upyet.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class, AlarmOccurrenceEntity::class, EvidenceSegmentEntity::class, TimerEntity::class],
    version = 5,
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
        )
    }
}
