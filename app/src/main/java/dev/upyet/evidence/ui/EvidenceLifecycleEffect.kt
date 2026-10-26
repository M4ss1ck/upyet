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
 * the screen starts [ringing] while it is up (the ringing state usually arrives after the first resume), and
 * the camera is released on stop and when the screen leaves composition. [currentRequest] must read the
 * ViewModel's live state, not a composed snapshot: composition stops collecting while the screen is stopped,
 * so on resume a snapshot can still describe a ring that has already ended.
 */
@Composable
fun EvidenceLifecycleEffect(session: EvidenceSession, ringing: Boolean, currentRequest: () -> EvidenceRequest?) {
    val host = LocalLifecycleOwner.current
    val latestProvider by rememberUpdatedState(currentRequest)
    DisposableEffect(host, session) {
        val binding = session.bind(host) { latestProvider() }
        onDispose { binding.dispose() }
    }
    LaunchedEffect(ringing) {
        if (ringing) session.onVisible(host, latestProvider())
    }
}
