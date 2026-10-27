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
    val snooze: SnoozePolicy,
    val evidenceEnabled: Boolean,
    val skipNextOn: LocalDate? = null,
    /** A one-time alarm an assistant created without its editor; deleted once dismissed (GLOSSARY.md, Voice alarm). */
    val voice: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)
