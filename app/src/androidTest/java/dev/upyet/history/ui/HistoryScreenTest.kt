package dev.upyet.history.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
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

    private fun show(state: HistoryUiState) {
        compose.setContent {
            UpYetTheme {
                Box(Modifier.size(width = 320.dp, height = 640.dp)) {
                    HistoryContent(state, onFilterChange = {}, onOpen = {}, onThumbnailNeeded = {})
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

    private fun labelHeight(text: String): Dp = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().height
}
