package dev.upyet.evidence.data

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.EvidenceErrorCode
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceStatus
import dev.upyet.evidence.domain.OccurrenceOutcome
import org.junit.Test
import java.time.Instant

class OccurrenceMapperTest {
    @Test
    fun roundTripsEveryOutcomeAndNulls() {
        OccurrenceOutcome.entries.forEach { outcome ->
            val occurrence =
                AlarmOccurrence(
                    OccurrenceId(1),
                    AlarmId(2),
                    Instant.ofEpochMilli(3),
                    null,
                    null,
                    null,
                    outcome,
                    null,
                )
            assertThat(
                occurrence.toEntity().toDomain(),
            ).isEqualTo(occurrence)
        }
    }

    @Test
    fun roundTripsEveryEvidenceStatusAndError() {
        EvidenceStatus.entries.forEach { status ->
            EvidenceErrorCode.entries.forEach { error ->
                val segment =
                    EvidenceSegment(
                        1,
                        OccurrenceId(2),
                        Instant.ofEpochMilli(3),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        status,
                        error,
                    )
                assertThat(
                    segment.toEntity().toDomain(),
                ).isEqualTo(segment)
            }
        }
    }
}
