package dev.myalarm.alarm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.myalarm.alarm.domain.Alarm
import dev.myalarm.alarm.domain.AlarmId
import dev.myalarm.alarm.domain.Recurrence
import dev.myalarm.core.ui.theme.UpYetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class AlarmListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rendersAlarmTimeAndLabel() {
        val alarm =
            Alarm(AlarmId(1), LocalTime.of(7, 30), true, "Wake up", Recurrence.OneTime, null, true, 9, false, Instant.EPOCH, Instant.EPOCH)
        compose.setContent { UpYetTheme { AlarmListContent(AlarmListUiState(listOf(alarm)), {}, {}) } }
        compose.onNodeWithText("7:30 AM").assertIsDisplayed()
        compose.onNodeWithText("Wake up").assertIsDisplayed()
    }
}
