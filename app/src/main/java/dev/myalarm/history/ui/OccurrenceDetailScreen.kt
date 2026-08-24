package dev.myalarm.history.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.myalarm.R
import dev.myalarm.evidence.domain.EvidenceStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun OccurrenceDetailScreen(onBack: () -> Unit, viewModel: OccurrenceDetailViewModel = hiltViewModel()) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    item?.let { value ->
        LazyColumn(Modifier.padding(16.dp)) {
            item { Text(stringResource(R.string.occurrence_details)) }
            item { Text(stringResource(R.string.scheduled_at, formatter.format(value.occurrence.scheduledFor))) }
            value.occurrence.triggeredAt?.let { instant -> item { Text(stringResource(R.string.triggered_at, formatter.format(instant))) } }
            value.occurrence.activityVisibleAt?.let { instant ->
                item { Text(stringResource(R.string.activity_visible_at, formatter.format(instant))) }
            }
            value.occurrence.dismissedAt?.let { instant -> item { Text(stringResource(R.string.dismissed_at, formatter.format(instant))) } }
            item { Text(stringResource(outcomeResource(value.occurrence.outcome))) }
            item { EvidencePlayer(value.segments, viewModel) }
            itemsIndexed(value.segments) { index, segment ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.evidence_segment_index, index + 1))
                    Text(stringResource(segmentStatusResource(segment.status)))
                    segment.startedAt?.let { Text(stringResource(R.string.segment_started_at, formatter.format(it))) }
                    segment.durationMs?.let {
                        val seconds = it / 1000
                        Text(pluralStringResource(R.plurals.segment_duration, seconds.toInt(), seconds))
                    }
                }
            }
            item {
                Button(onClick = { confirming = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete)) }
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

@Composable
private fun EvidencePlayer(segments: List<dev.myalarm.evidence.domain.EvidenceSegment>, viewModel: OccurrenceDetailViewModel) {
    val files = segments.mapNotNull { it.fileName?.let(viewModel::fileUri) }
    if (files.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(files) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(files.map { MediaItem.fromUri(it) })
            prepare()
        }
    }
    AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxWidth())
    DisposableEffect(Unit) { onDispose { player.release() } }
}

private fun segmentStatusResource(status: EvidenceStatus): Int = when (status) {
    EvidenceStatus.REQUESTED -> R.string.segment_requested
    EvidenceStatus.RECORDING -> R.string.segment_recording
    EvidenceStatus.RECORDED -> R.string.segment_recorded
    EvidenceStatus.PARTIAL -> R.string.segment_partial
    EvidenceStatus.FAILED -> R.string.segment_failed
    EvidenceStatus.SKIPPED -> R.string.segment_skipped
}
