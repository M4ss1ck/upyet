package dev.myalarm.alarm.ringing

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.myalarm.core.logging.AlarmLog

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
            val state by viewModel.state.collectAsStateWithLifecycle()
            RingingScreen(state, viewModel::dismiss, viewModel::snooze)
            // Only a session that existed and then ended may close this Activity; the initial LOADING
            // phase must never finish it, otherwise the alarm UI disappears before the service publishes.
            LaunchedEffect(state.phase) {
                if (state.phase == RingingPhase.ENDED) finish()
            }
        }
    }
}
