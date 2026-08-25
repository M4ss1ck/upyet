package dev.upyet.evidence.domain

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.data.EvidenceFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import java.io.File
import java.time.Instant

class DeleteOccurrenceWithEvidenceTest {
    @Test fun deletesFilesBeforeRow() = kotlinx.coroutines.test.runTest {
        val repository = FakeRepository(listOf(segment(EvidenceStatus.RECORDED)))
        val files = FakeFiles()
        assertThat(DeleteOccurrenceWithEvidence(repository, files)(OccurrenceId(1))).isEqualTo(DeleteOccurrenceResult.Deleted)
        assertThat(files.deleted).containsExactly("evidence.mp4")
        assertThat(repository.deleted).isTrue()
    }

    @Test fun blocksInProgressRecording() = kotlinx.coroutines.test.runTest {
        val repository = FakeRepository(listOf(segment(EvidenceStatus.RECORDING)))
        val files = FakeFiles()
        assertThat(DeleteOccurrenceWithEvidence(repository, files)(OccurrenceId(1))).isEqualTo(DeleteOccurrenceResult.InProgress)
        assertThat(files.deleted).isEmpty()
        assertThat(repository.deleted).isFalse()
    }

    private fun segment(status: EvidenceStatus) =
        EvidenceSegment(1, OccurrenceId(1), Instant.EPOCH, null, null, null, "evidence.mp4", null, null, status, null)

    private class FakeFiles : EvidenceFileStore {
        val deleted = mutableListOf<String>()
        override fun newEvidenceFile() = File("unused")
        override fun resolve(fileName: String) = File(fileName)
        override fun delete(fileName: String): Boolean {
            deleted += fileName
            return true
        }
        override fun deleteAll(fileNames: Iterable<String>) {
            fileNames.forEach { deleted += it }
        }
    }

    private class FakeRepository(private val values: List<EvidenceSegment>) : OccurrenceRepository {
        var deleted = false
        override fun observeOccurrences(): Flow<List<AlarmOccurrence>> = flowOf(emptyList())
        override fun observeOccurrence(id: OccurrenceId): Flow<AlarmOccurrence?> = flowOf(null)
        override fun observeSegments(occurrenceId: OccurrenceId): Flow<List<EvidenceSegment>> = flowOf(values)
        override fun observeOccurrenceWithSegments(id: OccurrenceId): Flow<OccurrenceRepository.OccurrenceWithEvidence?> = flowOf(null)
        override suspend fun createOccurrence(
            alarmId: AlarmId,
            scheduledFor: Instant,
            triggeredAt: Instant?,
            parentOccurrenceId: OccurrenceId?,
        ) = OccurrenceId(1)
        override suspend fun markActivityVisible(id: OccurrenceId, at: Instant) = Unit
        override suspend fun completeOccurrence(id: OccurrenceId, dismissedAt: Instant?, outcome: OccurrenceOutcome) = Unit
        override suspend fun insertSegment(segment: EvidenceSegment) = 1L
        override suspend fun updateSegment(segment: EvidenceSegment) = Unit
        override suspend fun deleteOccurrence(id: OccurrenceId) {
            deleted = true
        }
    }
}
