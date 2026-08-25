package dev.upyet.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun OccurrenceDetailScreen(onBack: () -> Unit, viewModel: OccurrenceDetailViewModel = hiltViewModel()) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val dateFormatter =
        rememberLocalized { locale -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone) }

    item?.let { value ->
        Column(Modifier.fillMaxSize()) {
            UpYetTopBar(
                title = value.label.ifBlank { stringResource(R.string.unnamed_alarm) },
                onBack = onBack,
                subtitle = dateFormatter.format(value.occurrence.scheduledFor),
                actions = {
                    val (container, content) = outcomeBadgeColors(value.occurrence.outcome)
                    StatusBadge(
                        text = stringResource(outcomeResource(value.occurrence.outcome)),
                        container = container,
                        content = content,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item { EvidencePlayer(value.segments, viewModel) }
                item { WhatHappenedTimeline(value.occurrence, zone) }
                itemsIndexed(value.segments) { index, segment ->
                    SegmentCard(index, segment, Modifier.padding(horizontal = 20.dp, vertical = 5.dp))
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
}

/**
 * The player and its clip-position caption. The ExoPlayer itself keeps the exact lifecycle the
 * previous implementation used - built once per file list and released in its own effect - with the
 * clip-index listener added and torn down separately so that contract is untouched.
 */
@Composable
private fun EvidencePlayer(segments: List<EvidenceSegment>, viewModel: OccurrenceDetailViewModel) {
    val files = segments.mapNotNull { it.fileName?.let(viewModel::fileUri) }
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

    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.fillMaxWidth()) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player } },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
            )
        }
        Text(
            stringResource(R.string.evidence_clip_position, currentIndex + 1, files.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
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
private fun SegmentCard(index: Int, segment: EvidenceSegment, modifier: Modifier = Modifier) {
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
