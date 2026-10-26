package dev.upyet.evidence.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.upyet.evidence.camera.EvidenceRequest
import dev.upyet.evidence.camera.EvidenceSession

/**
 * Binds [session] to the ringing screen that composes it: recording is requested on every resume and when
 * [request] first becomes available while the screen is up (the ringing state usually arrives after the
 * first resume), and the camera is released on stop and when the screen leaves composition. A null
 * [request] means the screen is not ringing yet, or any more, so nothing is recorded.
 */
@Composable
fun EvidenceLifecycleEffect(session: EvidenceSession, request: EvidenceRequest?) {
    val host = LocalLifecycleOwner.current
    val latestRequest by rememberUpdatedState(request)
    DisposableEffect(host, session) {
        val binding = session.bind(host) { latestRequest }
        onDispose { binding.dispose() }
    }
    val ringing = request != null
    LaunchedEffect(ringing) {
        if (ringing) session.onVisible(host, latestRequest)
    }
}
