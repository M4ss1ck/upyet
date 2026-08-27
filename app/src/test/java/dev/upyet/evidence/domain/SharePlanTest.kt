package dev.upyet.evidence.domain

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SharePlanTest {
    private val zone = ZoneId.of("UTC")

    private fun occurrence(
        id: Long,
        scheduledFor: Instant,
        outcome: OccurrenceOutcome = OccurrenceOutcome.DISMISSED,
        parentId: Long? = null,
    ) = AlarmOccurrence(
        id = OccurrenceId(id),
        alarmId = AlarmId(1),
        scheduledFor = scheduledFor,
        triggeredAt = null,
        activityVisibleAt = null,
        dismissedAt = null,
        outcome = outcome,
        parentOccurrenceId = parentId?.let { OccurrenceId(it) },
    )

    private fun segment(id: Long, occurrenceId: Long, fileName: String?, status: EvidenceStatus = EvidenceStatus.RECORDED) =
        EvidenceSegment(
            id = id,
            occurrenceId = OccurrenceId(occurrenceId),
            requestedAt = Instant.EPOCH,
            startedAt = null,
            endedAt = null,
            finalizedAt = null,
            fileName = fileName,
            durationMs = null,
            sizeBytes = null,
            status = status,
            errorCode = null,
        )

    @Test fun singleClipChainNaming() {
        val chain = OccurrenceChain(listOf(occurrence(1, Instant.parse("2026-08-26T07:30:00Z"))))
        val segments = mapOf(OccurrenceId(1) to listOf(segment(1, 1, "a.mp4")))
        val clips = shareableClips(chain, segments, zone)
        assertThat(clips).hasSize(1)
        assertThat(clips[0].fileName).isEqualTo("a.mp4")
        assertThat(clips[0].outgoingName).isEqualTo("upyet-2026-08-26-0730.mp4")
    }

    @Test fun threeSnoozeChainFourDifferentlyNamedClips() {
        val root = occurrence(1, Instant.parse("2026-08-26T07:00:00Z"), OccurrenceOutcome.SNOOZED)
        val s1 = occurrence(2, Instant.parse("2026-08-26T07:09:00Z"), OccurrenceOutcome.SNOOZED, 1)
        val s2 = occurrence(3, Instant.parse("2026-08-26T07:18:00Z"), OccurrenceOutcome.SNOOZED, 2)
        val last = occurrence(4, Instant.parse("2026-08-26T07:27:00Z"), OccurrenceOutcome.DISMISSED, 3)
        val chain = OccurrenceChain(listOf(root, s1, s2, last))
        val map = mapOf(
            OccurrenceId(1) to listOf(segment(1, 1, "f1.mp4")),
            OccurrenceId(2) to listOf(segment(2, 2, "f2.mp4")),
            OccurrenceId(3) to listOf(segment(3, 3, "f3.mp4")),
            OccurrenceId(4) to listOf(segment(4, 4, "f4.mp4")),
        )
        val clips = shareableClips(chain, map, zone)
        assertThat(clips).hasSize(4)
        assertThat(clips.map { it.outgoingName }).containsExactly(
            "upyet-2026-08-26-0700.mp4",
            "upyet-2026-08-26-0709.mp4",
            "upyet-2026-08-26-0718.mp4",
            "upyet-2026-08-26-0727.mp4",
        ).inOrder()
    }

    @Test fun linkWithTwoSegmentsProducesSuffix() {
        val chain = OccurrenceChain(listOf(occurrence(1, Instant.parse("2026-08-26T07:30:00Z"))))
        val segments = mapOf(
            OccurrenceId(1) to listOf(
                segment(1, 1, "a.mp4"),
                segment(2, 1, "b.mp4"),
                segment(3, 1, "c.mp4"),
            ),
        )
        val clips = shareableClips(chain, segments, zone)
        assertThat(clips).hasSize(3)
        assertThat(clips[0].outgoingName).isEqualTo("upyet-2026-08-26-0730.mp4")
        assertThat(clips[1].outgoingName).isEqualTo("upyet-2026-08-26-0730-2.mp4")
        assertThat(clips[2].outgoingName).isEqualTo("upyet-2026-08-26-0730-3.mp4")
    }

    @Test fun nullFileNameExcluded() {
        val chain = OccurrenceChain(listOf(occurrence(1, Instant.parse("2026-08-26T07:30:00Z"))))
        val segments = mapOf(
            OccurrenceId(1) to listOf(
                segment(1, 1, null, EvidenceStatus.RECORDED),
                segment(2, 1, "good.mp4", EvidenceStatus.RECORDED),
            ),
        )
        val clips = shareableClips(chain, segments, zone)
        assertThat(clips).hasSize(1)
        assertThat(clips[0].fileName).isEqualTo("good.mp4")
    }

    @Test fun failedAndSkippedExcluded() {
        val chain = OccurrenceChain(listOf(occurrence(1, Instant.parse("2026-08-26T07:30:00Z"))))
        val segments = mapOf(
            OccurrenceId(1) to listOf(
                segment(1, 1, "a.mp4", EvidenceStatus.FAILED),
                segment(2, 1, "b.mp4", EvidenceStatus.SKIPPED),
                segment(3, 1, "c.mp4", EvidenceStatus.REQUESTED),
                segment(4, 1, "d.mp4", EvidenceStatus.RECORDING),
                segment(5, 1, "e.mp4", EvidenceStatus.RECORDED),
                segment(6, 1, "f.mp4", EvidenceStatus.PARTIAL),
            ),
        )
        val clips = shareableClips(chain, segments, zone)
        assertThat(clips.map { it.fileName }).containsExactly("e.mp4", "f.mp4").inOrder()
    }

    @Test fun emptyWhenNoUsableEvidence() {
        val chain = OccurrenceChain(listOf(occurrence(1, Instant.parse("2026-08-26T07:30:00Z"))))
        val segments = mapOf(
            OccurrenceId(1) to listOf(
                segment(1, 1, null, EvidenceStatus.FAILED),
                segment(2, 1, "a.mp4", EvidenceStatus.FAILED),
            ),
        )
        val clips = shareableClips(chain, segments, zone)
        assertThat(clips).isEmpty()
    }

    @Test fun outgoingClipNameOrdinal1NoSuffix() {
        val name = outgoingClipName(Instant.parse("2026-08-26T07:30:00Z"), zone, 1)
        assertThat(name).isEqualTo("upyet-2026-08-26-0730.mp4")
    }

    @Test fun outgoingClipNameOrdinal2HasSuffix() {
        val name = outgoingClipName(Instant.parse("2026-08-26T07:30:00Z"), zone, 2)
        assertThat(name).isEqualTo("upyet-2026-08-26-0730-2.mp4")
    }

    @Test fun outgoingClipNameUsesZoneNotUtc() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 07:30 UTC is 09:30 in Berlin (summer, CEST)
        val name = outgoingClipName(Instant.parse("2026-08-26T07:30:00Z"), berlin, 1)
        assertThat(name).isEqualTo("upyet-2026-08-26-0930.mp4")
    }
}
