package dev.upyet.evidence.data

import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceStatus
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.evidence.domain.TimerAttribution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/** Before first unlock the ringing screen builds this sink; building it must not build the repository. */
class DeferredEvidenceSinkTest {
    private var resolved = 0
    private val writes = mutableListOf<String>()
    private val repository = Lazy<OccurrenceRepository> {
        resolved++
        RecordingRepository(writes)
    }

    @Test fun constructionDoesNotResolveTheRepository() {
        DeferredEvidenceSink(repository)

        assertThat(resolved).isEqualTo(0)
    }

    @Test fun everyWriteGoesToTheRepository() = runTest {
        val sink = DeferredEvidenceSink(repository)
        val segment = EvidenceSegment(0L, OccurrenceId(7), AT, null, null, null, null, null, null, EvidenceStatus.REQUESTED, null)

        sink.markActivityVisible(OccurrenceId(7), AT)
        val id = sink.insertSegment(segment)
        sink.updateSegment(segment.copy(id = id))

        assertThat(writes).containsExactly("visible:7", "insert", "update:42").inOrder()
    }

    /** Only the three evidence writes are expected; anything else reaching the repository is a test failure. */
    private class RecordingRepository(private val writes: MutableList<String>) : OccurrenceRepository {
        override suspend fun markActivityVisible(id: OccurrenceId, at: Instant) {
            writes += "visible:${id.value}"
        }

        override suspend fun insertSegment(segment: EvidenceSegment): Long {
            writes += "insert"
            return 42L
        }

        override suspend fun updateSegment(segment: EvidenceSegment) {
            writes += "update:${segment.id}"
        }

        override fun observeOccurrences(): Flow<List<AlarmOccurrence>> = error("unexpected")

        override fun observeOccurrence(id: OccurrenceId): Flow<AlarmOccurrence?> = error("unexpected")

        override fun observeSegments(occurrenceId: OccurrenceId): Flow<List<EvidenceSegment>> = error("unexpected")

        override fun observeOccurrenceWithSegments(id: OccurrenceId): Flow<OccurrenceRepository.OccurrenceWithEvidence?> =
            error("unexpected")

        override suspend fun createOccurrence(
            alarmId: AlarmId,
            scheduledFor: Instant,
            triggeredAt: Instant?,
            parentOccurrenceId: OccurrenceId?,
        ): OccurrenceId = error("unexpected")

        override suspend fun createTimerOccurrence(
            timers: List<TimerAttribution>,
            scheduledFor: Instant,
            triggeredAt: Instant,
        ): OccurrenceId = error("unexpected")

        override suspend fun updateTimerAttribution(id: OccurrenceId, timers: List<TimerAttribution>) = error("unexpected")

        override suspend fun interruptOrphanedTimerOccurrences() = error("unexpected")

        override suspend fun completeOccurrence(id: OccurrenceId, dismissedAt: Instant?, outcome: OccurrenceOutcome) = error("unexpected")

        override suspend fun deleteOccurrence(id: OccurrenceId) = error("unexpected")
    }

    private companion object {
        val AT: Instant = Instant.parse("2026-08-24T10:00:00Z")
    }
}
