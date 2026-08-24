package dev.myalarm.evidence.domain

import com.google.common.truth.Truth.assertThat
import dev.myalarm.alarm.domain.OccurrenceId
import org.junit.Test
import java.time.Instant

class EvidenceStateTransitionTest {
    private val requested =
        EvidenceSegment(1, OccurrenceId(2), Instant.EPOCH, null, null, null, "file", null, null, EvidenceStatus.REQUESTED, null)

    @Test fun legalRecordingTransitions() {
        val recording = requested.withStarted(Instant.ofEpochSecond(1))
        assertThat(recording.status).isEqualTo(EvidenceStatus.RECORDING)
        assertThat(
            recording.withFinalized(Instant.ofEpochSecond(2), EvidenceStatus.RECORDED, 1, 2).status,
        ).isEqualTo(EvidenceStatus.RECORDED)
        assertThat(recording.withFinalized(Instant.ofEpochSecond(2), EvidenceStatus.PARTIAL, 1, 2).status).isEqualTo(EvidenceStatus.PARTIAL)
        assertThat(
            recording.withFinalized(Instant.ofEpochSecond(2), EvidenceStatus.FAILED, null, null).status,
        ).isEqualTo(EvidenceStatus.FAILED)
    }

    @Test fun requestedCanBeFailedOrSkipped() {
        assertThat(
            requested.withUnavailable(Instant.ofEpochSecond(1), EvidenceStatus.FAILED, EvidenceErrorCode.UNKNOWN).status,
        ).isEqualTo(EvidenceStatus.FAILED)
        assertThat(
            requested.withUnavailable(Instant.ofEpochSecond(1), EvidenceStatus.SKIPPED, EvidenceErrorCode.EVIDENCE_DISABLED).status,
        ).isEqualTo(EvidenceStatus.SKIPPED)
    }
}
