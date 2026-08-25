package dev.upyet.evidence.ui

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun EvidencePreview(surfaceRequest: SurfaceRequest?) {
    if (surfaceRequest != null) {
        CameraXViewfinder(
            surfaceRequest = surfaceRequest,
            modifier = Modifier.fillMaxWidth().height(EvidencePreviewHeight).clip(MaterialTheme.shapes.extraLarge),
        )
    }
}

private val EvidencePreviewHeight = 214.dp
