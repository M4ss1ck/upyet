package dev.upyet.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class, AlarmOccurrenceEntity::class, EvidenceSegmentEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class UpYetDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    abstract fun occurrenceDao(): OccurrenceDao

    abstract fun evidenceSegmentDao(): EvidenceSegmentDao

    companion object {
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
        )
    }
}
