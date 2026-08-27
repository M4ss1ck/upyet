package dev.upyet.evidence.domain

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.OccurrenceId
import org.junit.Test
import java.time.Instant

class ThumbnailSelectionTest {
    @Test fun picksTheFirstSegmentThatProducedAFile() {
        val segments = listOf(
            segment(EvidenceStatus.FAILED, fileName = null),
            segment(EvidenceStatus.RECORDED, fileName = "second.mp4"),
            segment(EvidenceStatus.RECORDED, fileName = "third.mp4"),
        )
        assertThat(thumbnailSourceFileName(segments)).isEqualTo("second.mp4")
    }

    @Test fun hasNoSourceWhenNoSegmentProducedAFile() {
        val segments = listOf(segment(EvidenceStatus.FAILED, fileName = null), segment(EvidenceStatus.SKIPPED, fileName = null))
        assertThat(thumbnailSourceFileName(segments)).isNull()
    }

    @Test fun hasNoSourceWithoutSegments() {
        assertThat(thumbnailSourceFileName(emptyList())).isNull()
    }

    @Test fun fallsBackToTheFirstFrameWhenTheDurationIsUnknown() {
        assertThat(thumbnailFrameOffsetMicros(null)).isEqualTo(0L)
    }

    @Test fun fallsBackToTheFirstFrameForAnInvalidDuration() {
        assertThat(thumbnailFrameOffsetMicros(0L)).isEqualTo(0L)
    }

    @Test fun fallsBackToTheFirstFrameForANegativeDuration() {
        assertThat(thumbnailFrameOffsetMicros(-1L)).isEqualTo(0L)
    }

    @Test fun fallsBackToTheFirstFrameForAClipShorterThanTheMargin() {
        assertThat(thumbnailFrameOffsetMicros(80L)).isEqualTo(0L)
    }

    @Test fun backsOffFromTheEndForAClipShorterThanOneSecond() {
        assertThat(thumbnailFrameOffsetMicros(400L)).isEqualTo(300_000L)
    }

    @Test fun backsOffFromTheEndForAClipExactlyOneSecondLong() {
        assertThat(thumbnailFrameOffsetMicros(1_000L)).isEqualTo(900_000L)
    }

    @Test fun backsOffFromTheEndForALongerClip() {
        assertThat(thumbnailFrameOffsetMicros(1_500L)).isEqualTo(1_400_000L)
    }

    private fun segment(status: EvidenceStatus, fileName: String?) =
        EvidenceSegment(1, OccurrenceId(1), Instant.EPOCH, null, null, null, fileName, null, null, status, null)
}
