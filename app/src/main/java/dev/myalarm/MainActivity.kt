package dev.myalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.myalarm.core.ui.theme.MyAlarmTheme
import dev.myalarm.navigation.MyAlarmNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Constructed for its housekeeping side effect; the UI reads nothing from it yet.
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel
        setContent { MyAlarmTheme { MyAlarmNavHost() } }
    }
}
