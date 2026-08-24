package dev.myalarm.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minuteOfDay: Int,
    val enabled: Boolean,
    val label: String,
    val recurrenceType: String,
    val weekdayMask: Int,
    val soundUri: String?,
    val vibrationEnabled: Boolean,
    val snoozeMinutes: Int,
    val evidenceEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
