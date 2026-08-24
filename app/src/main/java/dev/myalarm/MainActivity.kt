package dev.myalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import dev.myalarm.core.ui.theme.MyAlarmTheme
import dev.myalarm.navigation.MyAlarmNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyAlarmTheme { MyAlarmNavHost() } }
    }
}
