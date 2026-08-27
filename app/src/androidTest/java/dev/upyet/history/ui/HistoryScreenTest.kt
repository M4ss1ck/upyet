package dev.upyet.history.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.core.ui.theme.UpYetTheme
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceStatus
import dev.upyet.evidence.domain.OccurrenceChain
import dev.upyet.evidence.domain.OccurrenceOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule val compose = createComposeRule()

    /**
     * The four filter labels are wider than a phone, so the row scrolls rather than squeezing the last
     * chip - which is how "Missed" ended up broken over two lines.
     */
    @Test fun theLastFilterChipKeepsItsLabelOnOneLine() {
        show(HistoryUiState())
        assertThat(labelHeight("Missed")).isEqualTo(labelHeight("All"))
    }

    @Test fun aRowWithAnExtractedFrameShowsTheFrame() {
        show(stateWith(fileName = "clip.mp4", thumbnails = mapOf("clip.mp4" to ImageBitmap(8, 8))))
        compose.onNodeWithTag(THUMBNAIL_FRAME_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun aRowWithAClipButNoFrameKeepsThePlayPlaceholder() {
        show(stateWith(fileName = "clip.mp4"))
        compose.onNodeWithTag(THUMBNAIL_PENDING_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun aRowWithoutAClipShowsTheNoEvidencePlaceholder() {
        show(stateWith(fileName = null))
        compose.onNodeWithTag(THUMBNAIL_NO_CLIP_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * A wake-up is one row, not one row per ring. The count and the elapsed time are the whole point of
     * the roll-up: they are the fact a user opens history to learn.
     */
    @Test fun aSnoozeChainCollapsesIntoOneRowThatCountsTheSnoozes() {
        show(chainState())

        compose.onNodeWithText("snoozed 2×", substring = true).assertIsDisplayed()
        compose.onNodeWithText("18 min", substring = true).assertIsDisplayed()
    }

    /**
     * The badge reports how the wake-up ended, not how its first ring ended. Both words also label a
     * filter chip, so the counts are what distinguish a badge from a chip: the chain ends dismissed, so
     * "Dismissed" appears twice and "Snoozed" only as its chip.
     */
    @Test fun theRowShowsTheChainsFinalOutcome() {
        show(chainState())

        compose.onAllNodesWithText("Dismissed").assertCountEquals(2)
        compose.onAllNodesWithText("Snoozed").assertCountEquals(1)
    }

    /** The frame worth showing is the one where they actually got up, which is the last ring's. */
    @Test fun theRowTakesItsFrameFromTheFinalRing() {
        val lastFrame = ImageBitmap(8, 8)
        show(chainState(thumbnails = mapOf("last.mp4" to lastFrame)))

        compose.onNodeWithTag(THUMBNAIL_FRAME_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun aWakeUpWithNoSnoozesShowsNoRollup() {
        show(stateWith(fileName = "clip.mp4"))

        compose.onNodeWithText("snoozed", substring = true).assertDoesNotExist()
    }

    /** Two rings 18 minutes apart, the first snoozed twice and the last dismissed. */
    private fun chainState(thumbnails: Map<String, ImageBitmap> = emptyMap()): HistoryUiState {
        val start = Instant.parse("2026-08-24T07:00:00Z")
        val first = occurrence(1, start, OccurrenceOutcome.SNOOZED, parent = null)
        val second = occurrence(2, start.plusSeconds(540), OccurrenceOutcome.SNOOZED, parent = OccurrenceId(1))
        val third = occurrence(
            id = 3,
            scheduledFor = start.plusSeconds(1080),
            outcome = OccurrenceOutcome.DISMISSED,
            parent = OccurrenceId(2),
            dismissedAt = start.plusSeconds(1080),
        )
        val chain = OccurrenceChain(listOf(first, second, third))
        val segments = mapOf(
            OccurrenceId(1) to listOf(segment(OccurrenceId(1), "first.mp4")),
            OccurrenceId(3) to listOf(segment(OccurrenceId(3), "last.mp4")),
        )
        val item = HistoryViewModel.HistoryItem(chain, "Wake up", segments)
        return HistoryUiState(
            groups = listOf(HistoryGroup(R.string.history_earlier, listOf(item))),
            isEmpty = false,
            thumbnails = thumbnails,
        )
    }

    private fun occurrence(
        id: Long,
        scheduledFor: Instant,
        outcome: OccurrenceOutcome,
        parent: OccurrenceId?,
        dismissedAt: Instant? = null,
    ) = AlarmOccurrence(
        id = OccurrenceId(id),
        alarmId = AlarmId(1),
        scheduledFor = scheduledFor,
        triggeredAt = scheduledFor,
        activityVisibleAt = null,
        dismissedAt = dismissedAt,
        outcome = outcome,
        parentOccurrenceId = parent,
    )

    private fun segment(occurrenceId: OccurrenceId, fileName: String) = EvidenceSegment(
        occurrenceId.value,
        occurrenceId,
        Instant.EPOCH,
        null,
        null,
        null,
        fileName,
        null,
        null,
        EvidenceStatus.RECORDED,
        null,
    )

    private fun show(state: HistoryUiState) {
        compose.setContent {
            UpYetTheme {
                Box(Modifier.size(width = 320.dp, height = 640.dp)) {
                    HistoryContent(state, onFilterChange = {}, onOpen = {}, onThumbnailNeeded = {}, onStatsWindowChange = {})
                }
            }
        }
    }

    private fun stateWith(fileName: String?, thumbnails: Map<String, ImageBitmap> = emptyMap()): HistoryUiState {
        val occurrenceId = OccurrenceId(1)
        val occurrence = AlarmOccurrence(
            id = occurrenceId,
            alarmId = AlarmId(1),
            scheduledFor = Instant.EPOCH,
            triggeredAt = null,
            activityVisibleAt = null,
            dismissedAt = null,
            outcome = OccurrenceOutcome.DISMISSED,
            parentOccurrenceId = null,
        )
        val status = if (fileName == null) EvidenceStatus.FAILED else EvidenceStatus.RECORDED
        val segment = EvidenceSegment(1, occurrenceId, Instant.EPOCH, null, null, null, fileName, null, null, status, null)
        val chain = OccurrenceChain(listOf(occurrence))
        val segmentsByOccurrence = mapOf(occurrenceId to listOf(segment))
        val item = HistoryViewModel.HistoryItem(chain, "Wake up", segmentsByOccurrence)
        return HistoryUiState(
            groups = listOf(HistoryGroup(R.string.history_earlier, listOf(item))),
            isEmpty = false,
            thumbnails = thumbnails,
        )
    }

    /** Scoped to the filter row: "Missed" is also a stats figure, so an unscoped text query finds two nodes. */
    private fun labelHeight(text: String): Dp =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(FILTER_ROW_TAG)), useUnmergedTree = true)
            .getUnclippedBoundsInRoot().height
}
