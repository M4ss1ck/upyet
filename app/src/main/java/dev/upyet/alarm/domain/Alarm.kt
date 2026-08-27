package dev.upyet.alarm.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class Alarm(
    val id: AlarmId,
    val time: LocalTime,
    val enabled: Boolean,
    val label: String,
    val recurrence: Recurrence,
    val soundUri: String?,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean,
    val snoozeMinutes: Int,
    val evidenceEnabled: Boolean,
    val skipNextOn: LocalDate? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
