package dev.myalarm.evidence.data

import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.OccurrenceId
import dev.myalarm.core.database.AlarmOccurrenceEntity
import dev.myalarm.evidence.domain.AlarmOccurrence
import dev.myalarm.evidence.domain.OccurrenceOutcome

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
