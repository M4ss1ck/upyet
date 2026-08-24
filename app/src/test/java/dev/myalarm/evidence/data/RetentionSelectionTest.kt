package dev.myalarm.evidence.data

import com.google.common.truth.Truth.assertThat
import dev.myalarm.core.database.EvidenceSegmentEntity
import dev.myalarm.settings.data.RetentionPolicy
import org.junit.Test

class RetentionSelectionTest {
    @Test fun doesNotSelectInProgressOrForever() {
        val segments = listOf(
            segment(1, "RECORDED"),
            segment(2, "REQUESTED"),
            segment(3, "RECORDING"),
        )
        assertThat(selectSegmentsForRetention(segments, RetentionPolicy.SEVEN_DAYS, 100)).containsExactly(segments[0])
        assertThat(selectSegmentsForRetention(segments, RetentionPolicy.FOREVER, 100)).isEmpty()
    }

    private fun segment(id: Long, status: String) = EvidenceSegmentEntity(
        id = id,
        occurrenceId = 1,
        requestedAt = 1,
        startedAt = null,
        endedAt = null,
        finalizedAt = null,
        fileName = null,
        durationMs = null,
        sizeBytes = null,
        status = status,
        errorCode = null,
    )
}
