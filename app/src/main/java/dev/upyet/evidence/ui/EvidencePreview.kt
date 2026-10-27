package dev.upyet.evidence.ui

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * The front-camera viewfinder, and also the consumer of the Preview use case's surface. CameraX starts the capture
 * session only once every bound use case has a surface, and gives up after five seconds, so while recording is wanted
 * this must stay composed even when the user has hidden the preview: without it VideoCapture never receives a frame
 * and the clip finalizes with no data. Hidden, it shrinks to [HiddenSize] instead of leaving the composition, so
 * showing it again resizes the same surface rather than tearing it down under a running recording.
 */
@Composable
fun EvidencePreview(surfaceRequest: SurfaceRequest?, visible: Boolean = true) {
    if (surfaceRequest != null) {
        CameraXViewfinder(
            surfaceRequest = surfaceRequest,
            modifier = if (visible) {
                Modifier.fillMaxWidth().height(EvidencePreviewHeight).clip(MaterialTheme.shapes.extraLarge)
            } else {
                Modifier.size(HiddenSize)
            },
        )
    }
}

private val EvidencePreviewHeight = 214.dp

/** Not zero: a view laid out at zero size may never be given a surface, and the surface is the whole point. */
private val HiddenSize = 1.dp
