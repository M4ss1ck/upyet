package dev.myalarm.evidence.domain

import dev.myalarm.alarm.domain.OccurrenceId
import dev.myalarm.evidence.data.EvidenceFileStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed interface DeleteOccurrenceResult {
    data object Deleted : DeleteOccurrenceResult
    data object InProgress : DeleteOccurrenceResult
}

class DeleteOccurrenceWithEvidence @Inject constructor(
    private val repository: OccurrenceRepository,
    private val files: EvidenceFileStore,
) {
    suspend operator fun invoke(id: OccurrenceId): DeleteOccurrenceResult {
        val segments = repository.observeSegments(id).first()
        if (segments.any { it.status == EvidenceStatus.REQUESTED || it.status == EvidenceStatus.RECORDING }) {
            return DeleteOccurrenceResult.InProgress
        }
        files.deleteAll(segments.mapNotNull { it.fileName })
        repository.deleteOccurrence(id)
        return DeleteOccurrenceResult.Deleted
    }
}
