package dev.myalarm.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alarm_occurrences",
    indices = [Index("alarmId")],
)
data class AlarmOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val scheduledFor: Long,
    val triggeredAt: Long?,
    val activityVisibleAt: Long?,
    val dismissedAt: Long?,
    val outcome: String,
    val parentOccurrenceId: Long?,
)
