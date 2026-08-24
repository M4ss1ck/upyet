package dev.myalarm.history.ui

import com.google.common.truth.Truth.assertThat
import dev.myalarm.R
import dev.myalarm.evidence.domain.EvidenceErrorCode
import dev.myalarm.evidence.domain.EvidenceStatus
import org.junit.Test

class EvidenceSummaryTest {
    @Test fun mapsPermissionFailure() {
        assertThat(evidenceSummaryResource(EvidenceStatus.FAILED, EvidenceErrorCode.PERMISSION_DENIED))
            .isEqualTo(R.string.evidence_summary_permission_denied)
    }

    @Test fun mapsRecorded() {
        assertThat(evidenceSummaryResource(EvidenceStatus.RECORDED, null)).isEqualTo(R.string.evidence_summary_recorded)
    }
}
