package dev.upyet.core.directboot

import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.EvidenceErrorCode
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceStatus
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.OccurrenceRepository
import dev.upyet.evidence.domain.TimerAttribution
import dev.upyet.testing.FakePendingOccurrences
import dev.upyet.timer.domain.TimerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/**
 * `USER_UNLOCKED` reconciliation: every pending record becomes a Room occurrence, dispatching on its source. Only an alarm
 * record may name a one-time alarm to retire (ADR 0010), so these pin that a timer record never retires one.
 */
class PendingOccurrenceReconcilerTest {
    private val scheduled = Instant.ofEpochMilli(1767250800000)
    private val triggered = Instant.ofEpochMilli(1767250805000)
    private val dismissed = Instant.ofEpochMilli(1767250900000)

    private val timerRecord = PendingTimerOccurrence(
        timers = listOf(TimerAttribution(TimerId(7), "Pasta")),
        triggeredAt = triggered,
        dismissedAt = dismissed,
        outcome = "STOPPED",
    )

    @Test
    fun anAlarmRecordCreatesAndCompletesAnAlarmOccurrenceWithoutEvidence() = runTest {
        val repo = RecordingOccurrenceRepository()
        val record = PendingOccurrence(AlarmId(7), scheduled, triggered, dismissed, "DISMISSED")

        val result = reconciler(repo, FakePendingOccurrences(record)).reconcile()

        assertThat(repo.alarmsCreated).containsExactly(
            RecordingOccurrenceRepository.AlarmCreated(OccurrenceId(1), AlarmId(7), scheduled, triggered, null),
        )
        assertThat(repo.completed).containsExactly(
            RecordingOccurrenceRepository.Completed(OccurrenceId(1), dismissed, OccurrenceOutcome.DISMISSED),
        )
        assertThat(repo.segments).isEmpty()
        assertThat(result.alarmsToRetire).containsExactly(AlarmId(7))
    }

    @Test
    fun aSnoozedAlarmRecordIsNotRetired() = runTest {
        val repo = RecordingOccurrenceRepository()
        val record = PendingOccurrence(AlarmId(7), scheduled, triggered, null, "SNOOZED")

        val result = reconciler(repo, FakePendingOccurrences(record)).reconcile()

        assertThat(result.alarmsToRetire).isEmpty()
        assertThat(repo.completed.single().outcome).isEqualTo(OccurrenceOutcome.SNOOZED)
    }

    @Test
    fun anAlarmRecordWithAnUnknownOutcomeCompletesWithError() = runTest {
        val repo = RecordingOccurrenceRepository()
        val record = PendingOccurrence(AlarmId(7), scheduled, triggered, null, "BOGUS")

        reconciler(repo, FakePendingOccurrences(record)).reconcile()

        assertThat(repo.completed.single().outcome).isEqualTo(OccurrenceOutcome.ERROR)
    }

    @Test
    fun aTimerRecordCreatesATimerOccurrenceWithOneSkippedSegment() = runTest {
        val repo = RecordingOccurrenceRepository()

        reconciler(repo, FakePendingOccurrences(timerRecord)).reconcile()

        assertThat(repo.alarmsCreated).isEmpty()
        assertThat(repo.timersCreated).containsExactly(
            RecordingOccurrenceRepository.TimerCreated(
                OccurrenceId(1),
                listOf(TimerAttribution(TimerId(7), "Pasta")),
                triggered,
                triggered,
            ),
        )
        assertThat(repo.segments).containsExactly(
            EvidenceSegment(
                id = 0L,
                occurrenceId = OccurrenceId(1),
                requestedAt = triggered,
                startedAt = null,
                endedAt = triggered,
                finalizedAt = triggered,
                fileName = null,
                durationMs = null,
                sizeBytes = null,
                status = EvidenceStatus.SKIPPED,
                errorCode = EvidenceErrorCode.DIRECT_BOOT_UNAVAILABLE,
            ),
        )
        assertThat(repo.completed).containsExactly(
            RecordingOccurrenceRepository.Completed(OccurrenceId(1), dismissed, OccurrenceOutcome.STOPPED),
        )
    }

    /** TimerId(7) and AlarmId(7) are different domains: a timer record must never retire the alarm with the same number. */
    @Test
    fun aTimerRecordNeverRetiresAnAlarmWithTheSameNumber() = runTest {
        val repo = RecordingOccurrenceRepository()

        val result = reconciler(repo, FakePendingOccurrences(timerRecord)).reconcile()

        assertThat(result.alarmsToRetire).isEmpty()
        assertThat(repo.alarmsCreated).isEmpty()
    }

    @Test
    fun mixedRecordsRetireOnlyTheAlarmAndCountEachSource() = runTest {
        val repo = RecordingOccurrenceRepository()
        val alarm = PendingOccurrence(AlarmId(7), scheduled, triggered, null, "RINGING")

        val result = reconciler(repo, FakePendingOccurrences(alarm, timerRecord)).reconcile()

        assertThat(result.alarmsToRetire).containsExactly(AlarmId(7)).inOrder()
        assertThat(result.alarms).isEqualTo(1)
        assertThat(result.timers).isEqualTo(1)
        assertThat(repo.alarmsCreated).hasSize(1)
        assertThat(repo.timersCreated).hasSize(1)
    }

    @Test
    fun theStoreIsClearedOnceAfterReconciliation() = runTest {
        val store = FakePendingOccurrences(
            PendingOccurrence(AlarmId(7), scheduled, triggered, null, "RINGING"),
            timerRecord,
        )

        reconciler(RecordingOccurrenceRepository(), store).reconcile()

        assertThat(store.all()).isEmpty()
        assertThat(store.cleared).isEqualTo(1)
    }

    @Test
    fun anEmptyStoreProducesAnEmptyResultAndTouchesNoRepositoryMethod() = runTest {
        val repo = RecordingOccurrenceRepository()
        val store = FakePendingOccurrences()

        val result = reconciler(repo, store).reconcile()

        assertThat(result).isEqualTo(PendingOccurrenceReconciler.Result(emptyList(), 0, 0))
        assertThat(repo.calls).isEmpty()
    }

    private fun reconciler(repo: OccurrenceRepository, store: PendingOccurrences) =
        PendingOccurrenceReconciler(store, Lazy<OccurrenceRepository> { repo })

    /**
     * Records the reconciliation writes and hands out occurrence ids 1, 2, ... in creation order. Only the writes the
     * reconciler makes are implemented; anything else fails, so an unexpected call cannot pass silently.
     */
    private class RecordingOccurrenceRepository : OccurrenceRepository {
        data class AlarmCreated(
            val id: OccurrenceId,
            val alarmId: AlarmId,
            val scheduledFor: Instant,
            val triggeredAt: Instant?,
            val parent: OccurrenceId?,
        )

        data class TimerCreated(
            val id: OccurrenceId,
            val timers: List<TimerAttribution>,
            val scheduledFor: Instant,
            val triggeredAt: Instant,
        )

        data class Completed(val id: OccurrenceId, val dismissedAt: Instant?, val outcome: OccurrenceOutcome)

        /** Every write in order, by method name. */
        val calls = mutableListOf<String>()
        val alarmsCreated = mutableListOf<AlarmCreated>()
        val timersCreated = mutableListOf<TimerCreated>()
        val segments = mutableListOf<EvidenceSegment>()
        val completed = mutableListOf<Completed>()

        private var nextId = 1L

        override suspend fun createOccurrence(
            alarmId: AlarmId,
            scheduledFor: Instant,
            triggeredAt: Instant?,
            parentOccurrenceId: OccurrenceId?,
        ): OccurrenceId {
            calls += "createOccurrence"
            val id = OccurrenceId(nextId++)
            alarmsCreated += AlarmCreated(id, alarmId, scheduledFor, triggeredAt, parentOccurrenceId)
            return id
        }

        override suspend fun createTimerOccurrence(
            timers: List<TimerAttribution>,
            scheduledFor: Instant,
            triggeredAt: Instant,
        ): OccurrenceId {
            calls += "createTimerOccurrence"
            val id = OccurrenceId(nextId++)
            timersCreated += TimerCreated(id, timers, scheduledFor, triggeredAt)
            return id
        }

        override suspend fun insertSegment(segment: EvidenceSegment): Long {
            calls += "insertSegment"
            segments += segment
            return 1L
        }

        override suspend fun completeOccurrence(id: OccurrenceId, dismissedAt: Instant?, outcome: OccurrenceOutcome) {
            calls += "completeOccurrence"
            completed += Completed(id, dismissedAt, outcome)
        }

        override suspend fun markActivityVisible(id: OccurrenceId, at: Instant) = error("unused")

        override suspend fun updateSegment(segment: EvidenceSegment) = error("unused")

        override fun observeOccurrences(): Flow<List<AlarmOccurrence>> = error("unused")

        override fun observeOccurrence(id: OccurrenceId): Flow<AlarmOccurrence?> = error("unused")

        override fun observeSegments(occurrenceId: OccurrenceId): Flow<List<EvidenceSegment>> = error("unused")

        override fun observeOccurrenceWithSegments(id: OccurrenceId): Flow<OccurrenceRepository.OccurrenceWithEvidence?> = error("unused")

        override suspend fun updateTimerAttribution(id: OccurrenceId, timers: List<TimerAttribution>) = error("unused")

        override suspend fun interruptOrphanedTimerOccurrences() = error("unused")

        override suspend fun deleteOccurrence(id: OccurrenceId) = error("unused")
    }
}
