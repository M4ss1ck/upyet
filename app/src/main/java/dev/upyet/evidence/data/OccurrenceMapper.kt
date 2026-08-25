package dev.upyet.evidence.data

import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.core.database.AlarmOccurrenceEntity
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.OccurrenceOutcome

fun AlarmOccurrenceEntity.toDomain(): AlarmOccurrence = AlarmOccurrence(
    id = OccurrenceId(id),
    alarmId = AlarmId(alarmId),
    scheduledFor =
    java.time.Instant.ofEpochMilli(
        scheduledFor,
    ),
    triggeredAt =
    triggeredAt?.let(
        java.time.Instant::ofEpochMilli,
    ),
    activityVisibleAt =
    activityVisibleAt?.let(
        java.time.Instant::ofEpochMilli,
    ),
    dismissedAt =
    dismissedAt?.let(
        java.time.Instant::ofEpochMilli,
    ),
    outcome =
    OccurrenceOutcome.valueOf(
        outcome,
    ),
    parentOccurrenceId =
    parentOccurrenceId?.let(
        ::OccurrenceId,
    ),
)

fun AlarmOccurrence.toEntity(): AlarmOccurrenceEntity = AlarmOccurrenceEntity(
    id = id.value,
    alarmId = alarmId.value,
    scheduledFor = scheduledFor.toEpochMilli(),
    triggeredAt = triggeredAt?.toEpochMilli(),
    activityVisibleAt =
    activityVisibleAt
        ?.toEpochMilli(),
    dismissedAt = dismissedAt?.toEpochMilli(),
    outcome = outcome.name,
    parentOccurrenceId = parentOccurrenceId?.value,
)
