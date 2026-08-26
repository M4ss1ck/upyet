package dev.upyet.history.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.upyet.R
import dev.upyet.core.ui.components.ClockText
import dev.upyet.core.ui.components.SectionLabel
import dev.upyet.core.ui.components.StatusBadge
import dev.upyet.core.ui.components.UpYetCard
import dev.upyet.core.ui.theme.MinTouchTarget
import dev.upyet.core.ui.theme.extraColors
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.OccurrenceOutcome
import dev.upyet.evidence.domain.thumbnailSourceFileName
import java.time.ZoneId

/** Test tags for the three thumbnail states; the frame itself is decorative and carries no description. */
internal const val THUMBNAIL_FRAME_TAG = "history_thumbnail_frame"
internal const val THUMBNAIL_PENDING_TAG = "history_thumbnail_pending"
internal const val THUMBNAIL_NO_CLIP_TAG = "history_thumbnail_no_clip"

@Composable
fun HistoryScreen(onOpen: (Long) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(
        state,
        onFilterChange = viewModel::setFilter,
        onOpen = onOpen,
        onThumbnailNeeded = viewModel::onThumbnailNeeded,
    )
}

/** Stateless history screen, so the layout can be exercised without Hilt or a ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryContent(
    state: HistoryUiState,
    onFilterChange: (HistoryFilter) -> Unit,
    onOpen: (Long) -> Unit,
    onThumbnailNeeded: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.history_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            // The four labels are wider than a phone, and a plain Row answers that by squeezing the last
            // chip until "Missed" breaks across lines. A scrolling row keeps every label on one line at
            // any width and in any language; the padding sits inside the scroll so it travels with the chips.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChipRow(state.filter, onFilterChange)
            }
        }
        when {
            state.isEmpty -> item { HistoryEmptyState(Modifier.padding(horizontal = 20.dp, vertical = 32.dp)) }

            state.isFilterEmpty -> item {
                HistoryEmptyState(Modifier.padding(horizontal = 20.dp, vertical = 32.dp), stringResource(R.string.history_filter_empty))
            }

            else -> historyGroups(state.groups, state.thumbnails, onOpen, onThumbnailNeeded)
        }
    }
}

@Composable
private fun FilterChipRow(selected: HistoryFilter, onFilterChange: (HistoryFilter) -> Unit) {
    val options = listOf(
        HistoryFilter.ALL to R.string.history_filter_all,
        HistoryFilter.DISMISSED to R.string.history_filter_dismissed,
        HistoryFilter.SNOOZED to R.string.history_filter_snoozed,
        HistoryFilter.MISSED to R.string.history_filter_missed,
    )
    options.forEach { (filter, labelRes) ->
        FilterChip(
            selected = filter == selected,
            onClick = { onFilterChange(filter) },
            label = { Text(stringResource(labelRes)) },
            modifier = Modifier.heightIn(min = MinTouchTarget),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = filter == selected,
                borderColor = MaterialTheme.colorScheme.outlineVariant,
                selectedBorderColor = Color.Transparent,
            ),
        )
    }
}

private fun LazyListScope.historyGroups(
    groups: List<HistoryGroup>,
    thumbnails: Map<String, ImageBitmap>,
    onOpen: (Long) -> Unit,
    onThumbnailNeeded: (String) -> Unit,
) {
    groups.forEach { group ->
        item {
            SectionLabel(stringResource(group.labelRes), Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
        items(group.items, key = { it.occurrence.id.value }) { historyItem ->
            OccurrenceRow(
                historyItem,
                thumbnails,
                onThumbnailNeeded,
                Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
            ) { onOpen(historyItem.occurrence.id.value) }
        }
    }
}

@Composable
private fun HistoryEmptyState(modifier: Modifier = Modifier, message: String = stringResource(R.string.history_empty)) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun OccurrenceRow(
    item: HistoryViewModel.HistoryItem,
    thumbnails: Map<String, ImageBitmap>,
    onThumbnailNeeded: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val occurrence = item.occurrence
    val zone = remember { ZoneId.systemDefault() }
    val time = remember(occurrence.scheduledFor, zone) { occurrence.scheduledFor.atZone(zone).toLocalTime() }
    val source = remember(item.segments) { thumbnailSourceFileName(item.segments) }
    LaunchedEffect(source) { source?.let(onThumbnailNeeded) }
    UpYetCard(modifier = modifier.clickable(onClick = onClick), contentPadding = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            HistoryThumbnail(bitmap = source?.let(thumbnails::get), hasClip = source != null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ClockText(time, style = MaterialTheme.typography.titleLarge)
                    Text(
                        item.label.ifBlank { stringResource(R.string.unnamed_alarm) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (container, content) = outcomeBadgeColors(occurrence.outcome)
                    StatusBadge(text = stringResource(outcomeResource(occurrence.outcome)), container = container, content = content)
                    evidenceSecondaryLine(item.segments)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun evidenceSecondaryLine(segments: List<EvidenceSegment>): String? {
    val clipCount = segments.size
    val durationMs = segments.sumOf { it.durationMs ?: 0L }
    val clipText = if (clipCount > 0) pluralStringResource(R.plurals.evidence_clip_count, clipCount, clipCount) else null
    val durationText = if (durationMs > 0) {
        val seconds = (durationMs / 1000).toInt()
        pluralStringResource(R.plurals.evidence_total_duration, seconds, seconds)
    } else {
        null
    }
    return listOfNotNull(clipText, durationText).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Three states and no fourth: the extracted frame, a clip whose frame is not there (yet, or at all), and
 * no clip. The icon state doubles as the loading state, so an arriving frame never shifts the layout.
 */
@Composable
private fun HistoryThumbnail(bitmap: ImageBitmap?, hasClip: Boolean, modifier: Modifier = Modifier) {
    val background = if (hasClip) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceVariant
    val tint = if (hasClip) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.outline
    Surface(shape = RoundedCornerShape(16.dp), color = background, modifier = modifier.size(62.dp)) {
        if (bitmap != null) {
            // Decorative: the row already announces its time, label and outcome to TalkBack.
            Image(
                bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().testTag(THUMBNAIL_FRAME_TAG),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (hasClip) Icons.Default.PlayArrow else Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.testTag(if (hasClip) THUMBNAIL_PENDING_TAG else THUMBNAIL_NO_CLIP_TAG),
                )
            }
        }
    }
}

internal fun outcomeResource(outcome: OccurrenceOutcome): Int = when (outcome) {
    OccurrenceOutcome.RINGING -> R.string.outcome_ringing
    OccurrenceOutcome.DISMISSED -> R.string.outcome_dismissed
    OccurrenceOutcome.SNOOZED -> R.string.outcome_snoozed
    OccurrenceOutcome.INTERRUPTED -> R.string.outcome_interrupted
    OccurrenceOutcome.TIMED_OUT -> R.string.outcome_timed_out
    OccurrenceOutcome.ERROR -> R.string.outcome_error
}

/** Container/content colour pair for an outcome badge. Colour never carries the meaning alone - the word is always there too. */
@Composable
internal fun outcomeBadgeColors(outcome: OccurrenceOutcome): Pair<Color, Color> = when (outcome) {
    OccurrenceOutcome.DISMISSED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

    OccurrenceOutcome.SNOOZED -> MaterialTheme.extraColors.warningContainer to MaterialTheme.extraColors.onWarningContainer

    OccurrenceOutcome.TIMED_OUT, OccurrenceOutcome.INTERRUPTED, OccurrenceOutcome.ERROR ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

    OccurrenceOutcome.RINGING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
}
