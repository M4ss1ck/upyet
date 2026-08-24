package dev.myalarm.evidence.data

import com.google.common.truth.Truth.assertThat
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.OccurrenceId
import dev.myalarm.evidence.domain.AlarmOccurrence
import dev.myalarm.evidence.domain.EvidenceErrorCode
import dev.myalarm.evidence.domain.EvidenceSegment
import dev.myalarm.evidence.domain.EvidenceStatus
import dev.myalarm.evidence.domain.OccurrenceOutcome
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
