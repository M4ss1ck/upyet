package dev.myalarm.evidence.data

import dev.myalarm.alarm.domain.OccurrenceId
import dev.myalarm.core.database.EvidenceSegmentEntity
import dev.myalarm.evidence.domain.EvidenceErrorCode
import dev.myalarm.evidence.domain.EvidenceSegment
import dev.myalarm.evidence.domain.EvidenceStatus

fun EvidenceSegmentEntity.toDomain(): EvidenceSegment = EvidenceSegment(
    id = id,
    occurrenceId = OccurrenceId(occurrenceId),
    requestedAt =
    java.time.Instant.ofEpochMilli(
        requestedAt,
    ),
    startedAt =
    startedAt?.let(
        java.time.Instant::ofEpochMilli,
    ),
    endedAt =
    endedAt?.let(
        java.time.Instant::ofEpochMilli,
    ),
    finalizedAt =
    finalizedAt?.let(
        java.time.Instant::ofEpochMilli,
    ),
    fileName = fileName,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    status = EvidenceStatus.valueOf(status),
    errorCode =
    errorCode?.let(
        EvidenceErrorCode::valueOf,
    ),
)

fun EvidenceSegment.toEntity(): EvidenceSegmentEntity = EvidenceSegmentEntity(
    id = id,
    occurrenceId = occurrenceId.value,
    requestedAt = requestedAt.toEpochMilli(),
    startedAt = startedAt?.toEpochMilli(),
    endedAt = endedAt?.toEpochMilli(),
    finalizedAt = finalizedAt?.toEpochMilli(),
    fileName = fileName,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    status = status.name,
    errorCode = errorCode?.name,
)
