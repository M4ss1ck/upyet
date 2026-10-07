package dev.upyet.history.ui

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import dev.upyet.R
import dev.upyet.core.ui.components.DestructiveButton
import dev.upyet.core.ui.components.SectionLabel
import dev.upyet.core.ui.components.StatusBadge
import dev.upyet.core.ui.components.UpYetCard
import dev.upyet.core.ui.components.UpYetTopBar
import dev.upyet.core.ui.rememberLocalized
import dev.upyet.core.ui.theme.extraColors
import dev.upyet.evidence.domain.AlarmOccurrence
import dev.upyet.evidence.domain.EvidenceSegment
import dev.upyet.evidence.domain.EvidenceStatus
import dev.upyet.evidence.domain.OccurrenceChain
import dev.upyet.evidence.domain.OccurrenceOutcome
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Portrait 9:16 is what the locked-portrait alarm screen records, and the frame's fallback until the first video size arrives. */
private const val PORTRAIT_ASPECT_RATIO = 9f / 16f

/** Caps how much of the scrolling detail page a tall portrait clip may claim. */
private val EvidenceFrameMaxHeight = 380.dp

@Composable
fun OccurrenceDetailScreen(onBack: () -> Unit, viewModel: OccurrenceDetailViewModel = hiltViewModel()) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    val shareExplainerShown by viewModel.shareExplainerShown.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }
    var showShareExplainer by remember { mutableStateOf(false) }
    var pendingShare by remember { mutableStateOf<(() -> Unit)?>(null) }
    val zone = remember { ZoneId.systemDefault() }
    val dateFormatter =
        rememberLocalized { locale -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone) }
    val context = LocalContext.current

    fun requestShare(action: () -> Unit) {
        if (!shareExplainerShown) {
            pendingShare = action
            showShareExplainer = true
        } else {
            action()
        }
    }

    item?.let { detail ->
        Column(Modifier.fillMaxSize()) {
            UpYetTopBar(
                title = detail.label.ifBlank { stringResource(R.string.unnamed_alarm) },
                onBack = onBack,
                subtitle = dateFormatter.format(detail.chain.root.scheduledFor),
                actions = {
                    val (container, content) = outcomeBadgeColors(detail.chain.finalOutcome)
                    StatusBadge(
                        text = stringResource(outcomeResource(detail.chain.finalOutcome)),
                        container = container,
                        content = content,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    if (detail.isShareable) {
                        val wholeSummary = shareSummary(detail, detail.shareableFileNames.size, zone)
                        IconButton(onClick = {
                            requestShare {
                                viewModel.share(wholeSummary) { intent -> context.startActivity(intent) }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_evidence))
                        }
                    }
                },
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    val files = remember(detail.allSegments) {
                        detail.allSegments.mapNotNull { segment -> segment.fileName?.let(viewModel::fileUri) }
                    }
                    EvidencePlayer(files)
                }
                if (detail.chain.containsSnooze) {
                    item {
                        SnoozeSummaryCard(detail.chain, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                }
                detail.chain.links.forEachIndexed { linkIndex, link ->
                    if (detail.chain.links.size > 1) {
                        item {
                            ChainRingHeader(
                                ringNumber = linkIndex + 1,
                                scheduledFor = link.scheduledFor,
                                outcome = link.outcome,
                                zone = zone,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                    item { WhatHappenedTimeline(link, zone) }
                    val segments = detail.segmentsByOccurrence[link.id].orEmpty()
                    itemsIndexed(segments) { index, segment ->
                        val canShare = segment.fileName != null &&
                            (segment.status == EvidenceStatus.RECORDED || segment.status == EvidenceStatus.PARTIAL) &&
                            detail.shareableFileNames.contains(segment.fileName)
                        val segmentSummary = shareSummary(detail, 1, zone)
                        SegmentCard(
                            index = index,
                            segment = segment,
                            canShare = canShare,
                            onShare = {
                                requestShare {
                                    viewModel.shareSegment(segment, segmentSummary) { intent -> context.startActivity(intent) }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                        )
                    }
                }
                item { DeleteSection(onDelete = { confirming = true }) }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.delete_occurrence_title)) },
            text = { Text(stringResource(R.string.delete_occurrence_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    viewModel.delete(onBack)
                }) { Text(stringResource(R.string.confirm_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showShareExplainer) {
        AlertDialog(
            onDismissRequest = {
                showShareExplainer = false
                pendingShare = null
            },
            title = { Text(stringResource(R.string.share_explainer_title)) },
            text = { Text(stringResource(R.string.share_explainer_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showShareExplainer = false
                    viewModel.markShareExplainerShown()
                    val action = pendingShare
                    pendingShare = null
                    action?.invoke()
                }) { Text(stringResource(R.string.share_explainer_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showShareExplainer = false
                    pendingShare = null
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun shareSummary(detail: OccurrenceDetailViewModel.OccurrenceDetail, clipCount: Int, zone: ZoneId): String {
    val dateFormatter =
        rememberLocalized { locale -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone) }
    val header = stringResource(R.string.share_summary_header, dateFormatter.format(detail.chain.root.scheduledFor))
    val label = detail.label.ifBlank { stringResource(R.string.unnamed_alarm) }
    val outcomeText = stringResource(outcomeResource(detail.chain.finalOutcome))
    val line2Base = stringResource(R.string.share_summary_alarm, label) + " · " + outcomeText
    val line2 = if (detail.chain.containsSnooze) {
        val countText = pluralStringResource(
            R.plurals.history_snooze_count,
            detail.chain.snoozeCount,
            detail.chain.snoozeCount,
        )
        val minutes = (detail.chain.elapsedMillis / 60_000L).toInt()
        val minutesText = if (minutes > 0) {
            pluralStringResource(R.plurals.history_chain_minutes, minutes, minutes)
        } else {
            null
        }
        val snoozeRollup = listOfNotNull(countText, minutesText).joinToString(" · ")
        "$line2Base · $snoozeRollup"
    } else {
        line2Base
    }
    val line3 = pluralStringResource(R.plurals.evidence_clip_count, clipCount, clipCount)
    return listOf(header, line2, line3).joinToString("\n")
}

@Composable
private fun SnoozeSummaryCard(chain: OccurrenceChain, modifier: Modifier = Modifier) {
    val countText = pluralStringResource(R.plurals.history_snooze_count, chain.snoozeCount, chain.snoozeCount)
    val minutes = (chain.elapsedMillis / 60_000L).toInt()
    val minutesText = if (minutes > 0) pluralStringResource(R.plurals.history_chain_minutes, minutes, minutes) else null
    val text = listOfNotNull(countText, minutesText).joinToString(" · ")
    UpYetCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ChainRingHeader(
    ringNumber: Int,
    scheduledFor: Instant,
    outcome: OccurrenceOutcome,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val timeFormatter =
        rememberLocalized { locale -> DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone) }
    val timeText = timeFormatter.format(scheduledFor)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.occurrence_chain_ring, ringNumber, timeText),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val (container, content) = outcomeBadgeColors(outcome)
        StatusBadge(text = stringResource(outcomeResource(outcome)), container = container, content = content)
    }
}

/**
 * The evidence clips play in a frame that takes the video's own aspect ratio, so a portrait
 * recording is shown upright at full width of the frame instead of pillarboxed inside a landscape
 * box. The transport sits below the frame rather than over it - the clips are portrait and short,
 * and an overlay controller covered most of what there was to see.
 *
 * The ExoPlayer keeps the exact lifecycle the previous implementation used - built once per file
 * list and released in its own effect - with the clip-index listener added and torn down separately
 * so that contract is untouched.
 */
@OptIn(UnstableApi::class)
@Composable
private fun EvidencePlayer(files: List<Uri>, modifier: Modifier = Modifier) {
    if (files.isEmpty()) return
    val context = LocalContext.current
    val player = remember(files) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(files.map { MediaItem.fromUri(it) })
            prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var currentIndex by remember(files) { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val presentation = rememberPresentationState(player)
    val videoSize = presentation.videoSizeDp
    val aspectRatio = videoSize
        ?.let { it.width / it.height }
        ?.takeIf { it.isFinite() && it > 0f }
        ?: PORTRAIT_ASPECT_RATIO

    Column(
        modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .heightIn(max = EvidenceFrameMaxHeight)
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.inverseSurface),
        ) {
            PlayerSurface(player, Modifier.fillMaxSize())
            // Until the first frame is decoded the surface is whatever was last on it; the shutter
            // keeps that from flashing through when a clip is switched.
            if (presentation.coverSurface) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.inverseSurface))
            }
        }
        EvidenceTransport(player, Modifier.padding(top = 12.dp))
        Text(
            stringResource(R.string.evidence_clip_position, currentIndex + 1, files.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun EvidenceTransport(player: Player, modifier: Modifier = Modifier) {
    val playPause = rememberPlayPauseButtonState(player)
    val previous = rememberPreviousButtonState(player)
    val next = rememberNextButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player)

    val durationMs = progress.durationMs.takeIf { it > 0L }
    // While the thumb is held the slider shows the drag, not the playhead; the seek lands on release
    // so a drag across a short clip does not fire a seek per frame.
    var scrubbed by remember { mutableStateOf<Float?>(null) }
    val playedFraction = durationMs?.let { (progress.currentPositionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f

    Column(modifier.fillMaxWidth()) {
        Slider(
            value = scrubbed ?: playedFraction,
            onValueChange = { scrubbed = it },
            onValueChangeFinished = {
                val target = scrubbed
                if (target != null && durationMs != null) player.seekTo((target * durationMs).toLong())
                scrubbed = null
            },
            enabled = durationMs != null,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackTime(progress.currentPositionMs)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = previous::onClick, enabled = previous.isEnabled) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.evidence_previous_clip))
                }
                FilledIconButton(onClick = playPause::onClick, enabled = playPause.isEnabled) {
                    Icon(
                        if (playPause.showPlay) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = stringResource(
                            if (playPause.showPlay) R.string.evidence_play else R.string.evidence_pause,
                        ),
                    )
                }
                IconButton(onClick = next::onClick, enabled = next.isEnabled) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.evidence_next_clip))
                }
            }
            PlaybackTime(durationMs ?: 0L)
        }
    }
}

@Composable
private fun PlaybackTime(millis: Long) {
    val seconds = (millis.coerceAtLeast(0L) / 1000L).toInt()
    Text(
        stringResource(R.string.evidence_playback_time, seconds / 60, seconds % 60),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private data class TimelineStep(val labelRes: Int, val at: Instant, val colorRole: TimelineColorRole)

private enum class TimelineColorRole { SCHEDULED, RANG, RECORDING, DISMISSED }

@Composable
private fun WhatHappenedTimeline(occurrence: AlarmOccurrence, zone: ZoneId, modifier: Modifier = Modifier) {
    val timeFormatter =
        rememberLocalized { locale -> DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone) }
    val steps = listOfNotNull(
        TimelineStep(R.string.timeline_scheduled, occurrence.scheduledFor, TimelineColorRole.SCHEDULED),
        occurrence.triggeredAt?.let { TimelineStep(R.string.timeline_rang, it, TimelineColorRole.RANG) },
        occurrence.activityVisibleAt?.let { TimelineStep(R.string.timeline_screen_visible, it, TimelineColorRole.RECORDING) },
        occurrence.dismissedAt?.let { TimelineStep(R.string.timeline_dismissed, it, TimelineColorRole.DISMISSED) },
    )
    UpYetCard(modifier = modifier.padding(horizontal = 20.dp), contentPadding = PaddingValues(20.dp)) {
        SectionLabel(stringResource(R.string.occurrence_timeline), Modifier.padding(bottom = 16.dp))
        steps.forEachIndexed { index, step ->
            TimelineStepRow(step, timeFormatter, isLast = index == steps.lastIndex)
        }
    }
}

@Composable
private fun timelineDotColor(role: TimelineColorRole): Color = when (role) {
    TimelineColorRole.SCHEDULED -> MaterialTheme.colorScheme.outline
    TimelineColorRole.RANG -> MaterialTheme.colorScheme.primary
    TimelineColorRole.RECORDING -> MaterialTheme.extraColors.recording
    TimelineColorRole.DISMISSED -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun TimelineStepRow(step: TimelineStep, timeFormatter: DateTimeFormatter, isLast: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(11.dp)) {
            Box(Modifier.size(11.dp).clip(CircleShape).background(timelineDotColor(step.colorRole)))
            if (!isLast) {
                Box(Modifier.padding(top = 4.dp).size(width = 2.dp, height = 26.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
        // Label and time are stacked, not set against each other across the row: side by side, a long
        // localised timestamp squeezed the label into two lines.
        Column(
            Modifier.fillMaxWidth().padding(bottom = if (isLast) 4.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                stringResource(step.labelRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                timeFormatter.format(step.at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SegmentCard(index: Int, segment: EvidenceSegment, canShare: Boolean, onShare: () -> Unit, modifier: Modifier = Modifier) {
    val (container, content, icon) = segmentTileStyle(segment.status)
    val zone = remember { ZoneId.systemDefault() }
    val timeFormatter =
        rememberLocalized { locale -> DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone) }
    UpYetCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = container, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.evidence_segment_index, index + 1) + " · " +
                        stringResource(segmentStatusResource(segment.status)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val details = listOfNotNull(
                    segment.startedAt?.let { stringResource(R.string.segment_started_at, timeFormatter.format(it)) },
                    segment.durationMs?.let {
                        val seconds = (it / 1000).toInt()
                        pluralStringResource(R.plurals.segment_duration, seconds, seconds)
                    },
                )
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (canShare) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_evidence))
                }
            }
        }
    }
}

private data class SegmentTileStyle(val container: Color, val content: Color, val icon: ImageVector)

@Composable
private fun segmentTileStyle(status: EvidenceStatus): SegmentTileStyle = when (status) {
    EvidenceStatus.RECORDED ->
        SegmentTileStyle(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Icons.Default.Videocam)

    EvidenceStatus.PARTIAL ->
        SegmentTileStyle(MaterialTheme.extraColors.warningContainer, MaterialTheme.extraColors.onWarningContainer, Icons.Default.Videocam)

    EvidenceStatus.FAILED, EvidenceStatus.SKIPPED ->
        SegmentTileStyle(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Default.VideocamOff)

    EvidenceStatus.REQUESTED, EvidenceStatus.RECORDING ->
        SegmentTileStyle(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Icons.Default.Videocam)
}

@Composable
private fun DeleteSection(onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DestructiveButton(
            text = stringResource(R.string.delete_occurrence),
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Delete,
        )
        Text(
            stringResource(R.string.delete_occurrence_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun segmentStatusResource(status: EvidenceStatus): Int = when (status) {
    EvidenceStatus.REQUESTED -> R.string.segment_requested
    EvidenceStatus.RECORDING -> R.string.segment_recording
    EvidenceStatus.RECORDED -> R.string.segment_recorded
    EvidenceStatus.PARTIAL -> R.string.segment_partial
    EvidenceStatus.FAILED -> R.string.segment_failed
    EvidenceStatus.SKIPPED -> R.string.segment_skipped
}
