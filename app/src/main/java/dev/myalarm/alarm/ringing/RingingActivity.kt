package dev.myalarm.alarm.ringing

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.myalarm.core.logging.AlarmLog
import dev.myalarm.core.ui.theme.MyAlarmTheme
import dev.myalarm.evidence.ui.EvidencePreview
import dev.myalarm.evidence.ui.RecordingIndicator

@AndroidEntryPoint
class RingingActivity : ComponentActivity() {
    private val viewModel: RingingViewModel by viewModels()

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
                    AlarmLog.event("ringing_back_ignored")
                }
            },
        )
        setContent {
            MyAlarmTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val evidenceState by viewModel.evidenceState.collectAsStateWithLifecycle()
                val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
                RingingScreen(
                    state = state,
                    onDismiss = viewModel::dismiss,
                    onSnooze = viewModel::snooze,
                    evidenceContent = {
                        RecordingIndicator(evidenceState)
                        EvidencePreview(surfaceRequest)
                    },
                )
                DisposableEffect(this@RingingActivity) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> viewModel.onRingingVisible(this@RingingActivity)
                            Lifecycle.Event.ON_STOP -> viewModel.onRingingHidden()
                            else -> Unit
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose {
                        lifecycle.removeObserver(observer)
                        viewModel.onRingingHidden()
                    }
                }
                // Only a session that existed and then ended may close this Activity; the initial LOADING
                // phase must never finish it, otherwise the alarm UI disappears before the service publishes.
                LaunchedEffect(state.phase) {
                    if (state.phase == RingingPhase.RINGING) {
                        viewModel.onRingingVisible(this@RingingActivity)
                    }
                    if (state.phase == RingingPhase.ENDED) finish()
                }
            }
        }
    }
}
