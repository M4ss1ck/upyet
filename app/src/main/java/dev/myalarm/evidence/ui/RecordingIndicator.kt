package dev.myalarm.evidence.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.myalarm.R
import dev.myalarm.evidence.camera.EvidenceRecordingState
import dev.myalarm.evidence.domain.EvidenceErrorCode

@Composable
fun RecordingIndicator(state: EvidenceRecordingState, modifier: Modifier = Modifier) {
    val (label, showDot, elapsed) = when (state) {
        EvidenceRecordingState.Idle -> Triple(stringResource(R.string.evidence_unavailable), false, null)

        EvidenceRecordingState.Preparing -> Triple(stringResource(R.string.evidence_preparing), false, null)

        is EvidenceRecordingState.Recording -> Triple(
            stringResource(R.string.evidence_recording),
            true,
            state.elapsedMillis / MILLIS_PER_SECOND,
        )

        is EvidenceRecordingState.Finished -> Triple(stringResource(R.string.evidence_finished), false, null)

        is EvidenceRecordingState.Unavailable -> Triple(errorLabel(state.errorCode), false, null)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showDot) {
            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = stringResource(R.string.evidence_recording_indicator),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(if (elapsed == null) label else stringResource(R.string.evidence_recording_elapsed, label, elapsed / 60, elapsed % 60))
    }
}

@Composable
private fun errorLabel(errorCode: EvidenceErrorCode): String = when (errorCode) {
    EvidenceErrorCode.PERMISSION_DENIED -> stringResource(R.string.evidence_permission_denied)
    EvidenceErrorCode.CAMERA_UNAVAILABLE -> stringResource(R.string.evidence_camera_unavailable)
    EvidenceErrorCode.CAMERA_IN_USE -> stringResource(R.string.evidence_camera_in_use)
    EvidenceErrorCode.DIRECT_BOOT_UNAVAILABLE -> stringResource(R.string.evidence_direct_boot_unavailable)
    EvidenceErrorCode.EVIDENCE_DISABLED -> stringResource(R.string.evidence_disabled)
    else -> stringResource(R.string.evidence_unavailable)
}

private const val MILLIS_PER_SECOND = 1_000L
