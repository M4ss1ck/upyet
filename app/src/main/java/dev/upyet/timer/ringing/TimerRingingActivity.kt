package dev.upyet.timer.ringing

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.core.logging.AlarmLog
import dev.upyet.core.ui.theme.UpYetTheme
import dev.upyet.evidence.ui.EvidenceLifecycleEffect
import dev.upyet.evidence.ui.EvidencePreview

/**
 * The timer ringing screen's host (AGENTS.md §9a): shown over the keyguard and turning the screen on through the
 * same API-level-safe path as `RingingActivity`, keeping the screen on, never dismissing the keyguard. It owns the
 * interaction and the evidence lifecycle only; `TimerPlaybackService` keeps ringing if this Activity dies.
 */
@AndroidEntryPoint
class TimerRingingActivity : ComponentActivity() {
    private val viewModel: TimerRingingViewModel by viewModels()

    // The user can unlock with the screen up, which need not resume it, so the glyph also follows USER_PRESENT.
    private var locked by mutableStateOf(false)

    private val userPresent = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            readKeyguard()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    AlarmLog.event("timer_ringing_back_ignored")
                }
            },
        )
        setContent {
            UpYetTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val evidenceState by viewModel.evidence.state.collectAsStateWithLifecycle()
                val surfaceRequest by viewModel.evidence.surfaceRequest.collectAsStateWithLifecycle()
                var previewVisible by rememberSaveable { mutableStateOf(false) }
                TimerRingingScreen(
                    state = state,
                    locked = locked,
                    evidenceState = evidenceState,
                    previewVisible = previewVisible,
                    onTogglePreview = { previewVisible = !previewVisible },
                    onFocus = viewModel::focus,
                    onStop = viewModel::stop,
                    onAddMinute = viewModel::addMinute,
                    onStopAll = viewModel::stopAll,
                    preview = { visible -> EvidencePreview(surfaceRequest, visible) },
                )
                // Keyed on recording, not just ringing: a recording-on timer joining a showing that was not recording
                // starts a segment (ADR 0010); the ViewModel stops it when the last recording-on timer leaves.
                EvidenceLifecycleEffect(
                    session = viewModel.evidence,
                    ringing = state.evidenceRequest != null,
                    currentRequest = viewModel::currentEvidenceRequest,
                )
                // Only a showing that existed and then ended may close this Activity; the initial LOADING phase
                // must never finish it, otherwise the screen disappears before the service publishes the ring.
                LaunchedEffect(state.phase) {
                    if (state.phase == TimerRingingPhase.ENDED) finish()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, userPresent, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        readKeyguard()
    }

    override fun onStop() {
        unregisterReceiver(userPresent)
        super.onStop()
    }

    private fun readKeyguard() {
        locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    }
}
