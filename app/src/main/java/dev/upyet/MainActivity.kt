package dev.upyet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.core.ui.theme.UpYetTheme
import dev.upyet.navigation.UpYetNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Constructed for its housekeeping side effect; the UI reads nothing from it yet.
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel
        setContent { UpYetTheme { UpYetNavHost() } }
    }
}
