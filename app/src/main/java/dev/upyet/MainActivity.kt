package dev.upyet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.core.ui.theme.UpYetTheme
import dev.upyet.navigation.EXTRA_ALARM_PREFILL
import dev.upyet.navigation.EXTRA_DESTINATION
import dev.upyet.navigation.LaunchDestination
import dev.upyet.navigation.UpYetNavHost
import dev.upyet.navigation.decodeLaunchDestination
import dev.upyet.navigation.toExtras

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Constructed for its housekeeping side effect; the UI reads nothing from it yet.
    private val viewModel: MainViewModel by viewModels()

    private var destination by mutableStateOf<LaunchDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel
        // A recreated activity keeps its own navigation state; replaying the launch destination would undo the user's moves.
        if (savedInstanceState == null) destination = destinationOf(intent)
        setContent { UpYetTheme { UpYetNavHost(destination, onDestinationConsumed = { destination = null }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        destinationOf(intent)?.let { destination = it }
    }

    /** Only the trampoline's intents carry [EXTRA_DESTINATION]; the launcher intent, and anything else, is ignored. */
    private fun destinationOf(intent: Intent): LaunchDestination? =
        decodeLaunchDestination(intent.getStringExtra(EXTRA_DESTINATION), intent.getStringExtra(EXTRA_ALARM_PREFILL))

    companion object {
        fun destinationIntent(context: Context, destination: LaunchDestination): Intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { destination.toExtras().forEach { (key, value) -> putExtra(key, value) } }
    }
}
