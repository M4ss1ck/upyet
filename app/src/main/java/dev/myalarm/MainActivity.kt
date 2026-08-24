package dev.myalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.myalarm.core.ui.theme.MyAlarmTheme
import dev.myalarm.evidence.data.RetentionCleaner
import dev.myalarm.navigation.MyAlarmNavHost
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var retentionCleaner: RetentionCleaner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { retentionCleaner.clean() }
        setContent { MyAlarmTheme { MyAlarmNavHost() } }
    }
}
