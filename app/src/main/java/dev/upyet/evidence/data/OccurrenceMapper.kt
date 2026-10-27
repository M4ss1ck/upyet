package dev.upyet.evidence.data

import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.core.database.AlarmOccurrenceEntity
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceSource

/** A row with no or an unknown source reads back as an alarm, so an older build's rows and future values never break history. */
private fun AlarmOccurrenceEntity.occurrenceSource(): OccurrenceSource =
    OccurrenceSource.entries.firstOrNull { it.name == source } ?: OccurrenceSource.ALARM

fun AlarmOccurrenceEntity.toDomain(): AlarmOccurrence {
    val domainSource = occurrenceSource()
    val isTimer = domainSource == OccurrenceSource.TIMER
    return AlarmOccurrence(
        id = OccurrenceId(id),
        alarmId = if (isTimer) null else AlarmId(alarmId),
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
        source = domainSource,
        timers = if (isTimer) TimerAttributionCodec.decode(timerAttribution) else emptyList(),
    )
}

fun AlarmOccurrence.toEntity(): AlarmOccurrenceEntity = AlarmOccurrenceEntity(
    id = id.value,
    alarmId = alarmId?.value ?: AlarmOccurrenceEntity.NO_ALARM_ID,
    scheduledFor = scheduledFor.toEpochMilli(),
    triggeredAt = triggeredAt?.toEpochMilli(),
    activityVisibleAt =
    activityVisibleAt
        ?.toEpochMilli(),
    dismissedAt = dismissedAt?.toEpochMilli(),
    outcome = outcome.name,
    parentOccurrenceId = parentOccurrenceId?.value,
    source = source.name,
    timerAttribution = if (source == OccurrenceSource.TIMER) TimerAttributionCodec.encode(timers) else null,
)
