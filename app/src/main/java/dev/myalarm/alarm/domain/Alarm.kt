package dev.myalarm.alarm.domain

import java.time.Instant
import java.time.LocalTime

data class Alarm(
    val id: AlarmId,
    val time: LocalTime,
    val enabled: Boolean,
    val label: String,
    val recurrence: Recurrence,
    val soundUri: String?,
    val vibrationEnabled: Boolean,
    val snoozeMinutes: Int,
    val evidenceEnabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
