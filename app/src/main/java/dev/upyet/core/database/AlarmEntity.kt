package dev.upyet.core.database

import androidx.room.ColumnInfo
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
    @ColumnInfo(defaultValue = "1") val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean,
    val snoozeMinutes: Int,
    val evidenceEnabled: Boolean,
    val skipNextOnEpochDay: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
