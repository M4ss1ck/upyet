package dev.myalarm.alarm.ringing

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.myalarm.core.ui.theme.MyAlarmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RingingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsRemainAvailableWhenEvidenceFails() {
        compose.setContent {
            MyAlarmTheme {
                RingingScreen(RingingUiState(currentTime = LocalTime.of(7, 0), snoozeMinutes = 9), {}, {}) {
                    Text("Evidence unavailable", Modifier.padding(8.dp).semantics { contentDescription = "Evidence recording in progress" })
                }
            }
        }
        compose.onNodeWithText("Snooze 9 minutes").assertIsEnabled().assertHasClickAction()
        compose.onNodeWithText("Dismiss alarm").assertIsEnabled().assertHasClickAction()
        compose.onNodeWithContentDescription("Evidence recording in progress").assertIsDisplayed()
    }
}
