package dev.upyet.evidence.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.upyet.R
import dev.upyet.core.ui.theme.RingingPalette
import dev.upyet.evidence.camera.EvidenceRecordingState
import dev.upyet.evidence.domain.EvidenceErrorCode

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
    // Created unconditionally so the animation API is never called from inside a conditional; the value
    // is only ever applied to the dot, which is itself only emitted while recording.
    val dotAlpha by rememberInfiniteTransition(label = "recording-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "recording-dot-alpha",
    )
    Row(
        // This chip is designed to sit as an overlay on the evidence preview beneath it: the inset keeps
        // it clear of the panel edge and the zIndex keeps it painted in front regardless of call order.
        modifier
            .zIndex(1f)
            .padding(14.dp)
            .background(RingingPalette.background.copy(alpha = 0.72f), CircleShape)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDot) {
            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = stringResource(R.string.evidence_recording_indicator),
                tint = RingingPalette.recording,
                modifier = Modifier.size(9.dp).alpha(dotAlpha),
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            if (elapsed == null) label else stringResource(R.string.evidence_recording_elapsed, label, elapsed / 60, elapsed % 60),
            style = MaterialTheme.typography.labelMedium,
            color = RingingPalette.onBackground,
        )
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
private const val PULSE_MILLIS = 800
