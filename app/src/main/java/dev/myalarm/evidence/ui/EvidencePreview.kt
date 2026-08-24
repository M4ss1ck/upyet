package dev.myalarm.evidence.ui

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EvidencePreview(surfaceRequest: SurfaceRequest?) {
    if (surfaceRequest != null) {
        CameraXViewfinder(surfaceRequest = surfaceRequest, modifier = Modifier.size(width = 120.dp, height = 68.dp))
    }
}
