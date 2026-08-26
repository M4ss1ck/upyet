package dev.upyet.evidence.domain

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import org.junit.Test
import java.time.Instant

class OccurrenceChainTest {
    private fun occurrence(
        id: Long,
        scheduledFor: Instant,
        outcome: OccurrenceOutcome = OccurrenceOutcome.DISMISSED,
        parentId: Long? = null,
        triggeredAt: Instant? = null,
        dismissedAt: Instant? = null,
    ) = AlarmOccurrence(
        id = OccurrenceId(id),
        alarmId = AlarmId(1),
        scheduledFor = scheduledFor,
        triggeredAt = triggeredAt,
        activityVisibleAt = null,
        dismissedAt = dismissedAt,
        outcome = outcome,
        parentOccurrenceId = parentId?.let { OccurrenceId(it) },
    )

    private fun segment(occurrenceId: Long, fileName: String?) = EvidenceSegment(
        id = 1,
        occurrenceId = OccurrenceId(occurrenceId),
        requestedAt = Instant.EPOCH,
        startedAt = null,
        endedAt = null,
        finalizedAt = null,
        fileName = fileName,
        durationMs = null,
        sizeBytes = null,
        status = if (fileName == null) EvidenceStatus.FAILED else EvidenceStatus.RECORDED,
        errorCode = null,
    )

    @Test fun loneOccurrenceIsOneLinkChain() {
        val single = occurrence(1, Instant.parse("2026-08-24T07:00:00Z"))
        val chains = buildOccurrenceChains(listOf(single))
        assertThat(chains).hasSize(1)
        assertThat(chains[0].links).hasSize(1)
        assertThat(chains[0].root.id).isEqualTo(OccurrenceId(1))
        assertThat(chains[0].last.id).isEqualTo(OccurrenceId(1))
        assertThat(chains[0].snoozeCount).isEqualTo(0)
        assertThat(chains[0].containsSnooze).isFalse()
    }

    @Test fun threeLinkChainAssemblesInTimeOrderWithSnoozeCount2() {
        val root = occurrence(1, Instant.parse("2026-08-24T07:00:00Z"), OccurrenceOutcome.SNOOZED, null)
        val snooze1 = occurrence(2, Instant.parse("2026-08-24T07:09:00Z"), OccurrenceOutcome.SNOOZED, 1)
        val final = occurrence(3, Instant.parse("2026-08-24T07:18:00Z"), OccurrenceOutcome.DISMISSED, 2)
        // Shuffle input order to ensure assembly is not order-dependent.
        val chains = buildOccurrenceChains(listOf(final, root, snooze1))
        assertThat(chains).hasSize(1)
        val chain = chains[0]
        assertThat(chain.links.map { it.id }).containsExactly(OccurrenceId(1), OccurrenceId(2), OccurrenceId(3)).inOrder()
        assertThat(chain.snoozeCount).isEqualTo(2)
        assertThat(chain.containsSnooze).isTrue()
    }

    @Test fun finalOutcomeIsLastLinks() {
        val root = occurrence(1, Instant.parse("2026-08-24T07:00:00Z"), OccurrenceOutcome.SNOOZED)
        val final = occurrence(2, Instant.parse("2026-08-24T07:09:00Z"), OccurrenceOutcome.DISMISSED, 1)
        val chains = buildOccurrenceChains(listOf(root, final))
        assertThat(chains[0].finalOutcome).isEqualTo(OccurrenceOutcome.DISMISSED)
    }

    @Test fun orphanWhoseParentAbsentBecomesOwnRoot() {
        val root = occurrence(1, Instant.parse("2026-08-24T07:00:00Z"))
        // Parent 99 does not exist in the list.
        val orphan = occurrence(2, Instant.parse("2026-08-24T07:10:00Z"), parentId = 99)
        val chains = buildOccurrenceChains(listOf(root, orphan))
        assertThat(chains).hasSize(2)
        // Both are roots now; verify orphan is its own chain.
        val orphanChain = chains.first { it.root.id == OccurrenceId(2) }
        assertThat(orphanChain.links).hasSize(1)
    }

    @Test fun twoIndependentChainsBothAppearNewestRootFirst() {
        val earlierRoot = occurrence(1, Instant.parse("2026-08-24T06:00:00Z"))
        val laterRoot = occurrence(2, Instant.parse("2026-08-24T07:00:00Z"))
        val chains = buildOccurrenceChains(listOf(earlierRoot, laterRoot))
        assertThat(chains).hasSize(2)
        assertThat(chains[0].root.id).isEqualTo(OccurrenceId(2))
        assertThat(chains[1].root.id).isEqualTo(OccurrenceId(1))
    }

    @Test fun elapsedMillisUsesDismissedAtWhenPresent() {
        val root = occurrence(1, Instant.parse("2026-08-24T07:00:00Z"))
        // 18 minutes after root.
        val dismissed = Instant.parse("2026-08-24T07:18:00Z")
        val final = occurrence(2, Instant.parse("2026-08-24T07:18:00Z"), parentId = 1, dismissedAt = dismissed)
        val chains = buildOccurrenceChains(listOf(root, final))
        assertThat(chains[0].elapsedMillis).isEqualTo(18 * 60 * 1000L)
    }

    @Test fun elapsedMillisFallsBackWhenDismissedAbsent() {
        val root = occurrence(1, Instant.parse("2026-08-24T07:00:00Z"))
        val triggered = Instant.parse("2026-08-24T07:10:00Z")
        val final = occurrence(2, Instant.parse("2026-08-24T07:18:00Z"), parentId = 1, triggeredAt = triggered)
        val chains = buildOccurrenceChains(listOf(root, final))
        assertThat(chains[0].elapsedMillis).isEqualTo(10 * 60 * 1000L)
    }

    @Test fun elapsedMillisFallsBackToScheduledForWhenNoEndTimes() {
        val root = occurrence(1, Instant.parse("2026-08-24T07:00:00Z"))
        val final = occurrence(2, Instant.parse("2026-08-24T07:18:00Z"), parentId = 1)
        val chains = buildOccurrenceChains(listOf(root, final))
        assertThat(chains[0].elapsedMillis).isEqualTo(18 * 60 * 1000L)
    }

    @Test fun elapsedMillisFloorsAtZero() {
        // last ends before root — should floor at 0.
        val root = occurrence(1, Instant.parse("2026-08-24T07:18:00Z"))
        val final = occurrence(
            2,
            Instant.parse("2026-08-24T07:18:00Z"),
            parentId = 1,
            dismissedAt = Instant.parse("2026-08-24T07:00:00Z"),
        )
        val chains = buildOccurrenceChains(listOf(root, final))
        assertThat(chains[0].elapsedMillis).isEqualTo(0L)
    }

    @Test fun cycleDoesNotHang() {
        // 1 -> 2 -> 1 cycle (both parents present). Must terminate.
        val a = occurrence(1, Instant.parse("2026-08-24T07:00:00Z"), parentId = 2)
        val b = occurrence(2, Instant.parse("2026-08-24T07:09:00Z"), parentId = 1)
        val chains = buildOccurrenceChains(listOf(a, b))
        // Assert termination and something sane: at least one chain, each link belongs to input.
        assertThat(chains).isNotEmpty()
        val allIds = chains.flatMap { it.links }.map { it.id }.toSet()
        assertThat(allIds).containsAtLeast(OccurrenceId(1), OccurrenceId(2))
    }

    @Test fun emptyInputYieldsEmptyOutput() {
        assertThat(buildOccurrenceChains(emptyList())).isEmpty()
    }

    // chainThumbnailSourceFileName tests

    @Test fun thumbnailPrefersLastLinksClip() {
        val chain = OccurrenceChain(
            listOf(
                occurrence(1, Instant.parse("2026-08-24T07:00:00Z")),
                occurrence(2, Instant.parse("2026-08-24T07:09:00Z"), parentId = 1),
            ),
        )
        val map = mapOf(
            OccurrenceId(1) to listOf(segment(1, "first.mp4")),
            OccurrenceId(2) to listOf(segment(2, "last.mp4")),
        )
        assertThat(chainThumbnailSourceFileName(chain, map)).isEqualTo("last.mp4")
    }

    @Test fun thumbnailFallsBackToEarlierLinkWhenLastHasNone() {
        val chain = OccurrenceChain(
            listOf(
                occurrence(1, Instant.parse("2026-08-24T07:00:00Z")),
                occurrence(2, Instant.parse("2026-08-24T07:09:00Z"), parentId = 1),
            ),
        )
        val map = mapOf(
            OccurrenceId(1) to listOf(segment(1, "first.mp4")),
            OccurrenceId(2) to listOf(segment(2, null)),
        )
        assertThat(chainThumbnailSourceFileName(chain, map)).isEqualTo("first.mp4")
    }

    @Test fun thumbnailNullWhenNoLinkHasClip() {
        val chain = OccurrenceChain(
            listOf(
                occurrence(1, Instant.parse("2026-08-24T07:00:00Z")),
                occurrence(2, Instant.parse("2026-08-24T07:09:00Z"), parentId = 1),
            ),
        )
        val map = mapOf(
            OccurrenceId(1) to listOf(segment(1, null)),
            OccurrenceId(2) to listOf(segment(2, null)),
        )
        assertThat(chainThumbnailSourceFileName(chain, map)).isNull()
    }
}
