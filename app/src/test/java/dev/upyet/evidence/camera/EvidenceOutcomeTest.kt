package dev.upyet.evidence.camera

import com.google.common.truth.Truth.assertThat
import dev.upyet.evidence.domain.EvidenceErrorCode
import dev.upyet.evidence.domain.EvidenceStatus
import org.junit.Test

class EvidenceOutcomeTest {
    @Test
    fun noErrorIsRecorded() {
        assertThat(statusForFinalize(0, false, 0L)).isEqualTo(EvidenceStatus.RECORDED to null)
    }

    @Test
    fun recoverableErrorsWithDurationArePartial() {
        assertThat(statusForFinalize(1, true, 1L)).isEqualTo(EvidenceStatus.PARTIAL to null)
        assertThat(statusForFinalize(2, true, 1L)).isEqualTo(EvidenceStatus.PARTIAL to null)
        assertThat(statusForFinalize(3, true, 1L)).isEqualTo(EvidenceStatus.PARTIAL to EvidenceErrorCode.INTERRUPTED)
    }

    @Test
    fun recoverableErrorsWithoutDurationFail() {
        assertThat(statusForFinalize(1, true, 0L)).isEqualTo(EvidenceStatus.FAILED to EvidenceErrorCode.FINALIZATION_FAILED)
        assertThat(statusForFinalize(2, false, 1L)).isEqualTo(EvidenceStatus.FAILED to EvidenceErrorCode.FINALIZATION_FAILED)
        assertThat(statusForFinalize(3, true, 0L)).isEqualTo(EvidenceStatus.FAILED to EvidenceErrorCode.FINALIZATION_FAILED)
    }

    @Test
    fun fatalErrorsMapToTheirErrorCodes() {
        assertThat(statusForFinalize(4, true, 1L)).isEqualTo(EvidenceStatus.FAILED to EvidenceErrorCode.STORAGE_ERROR)
        assertThat(statusForFinalize(5, true, 1L)).isEqualTo(EvidenceStatus.FAILED to EvidenceErrorCode.FINALIZATION_FAILED)
        assertThat(statusForFinalize(6, true, 1L)).isEqualTo(EvidenceStatus.FAILED to EvidenceErrorCode.FINALIZATION_FAILED)
        assertThat(statusForFinalize(7, true, 1L)).isEqualTo(EvidenceStatus.FAILED to EvidenceErrorCode.FINALIZATION_FAILED)
    }

    @Test
    fun unknownErrorsMapToUnknown() {
        assertThat(statusForFinalize(-1, true, 1L)).isEqualTo(EvidenceStatus.FAILED to EvidenceErrorCode.UNKNOWN)
        assertThat(statusForFinalize(99, false, 0L)).isEqualTo(EvidenceStatus.FAILED to EvidenceErrorCode.UNKNOWN)
    }
}
