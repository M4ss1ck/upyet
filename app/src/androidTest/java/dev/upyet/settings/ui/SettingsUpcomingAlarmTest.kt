package dev.upyet.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.upyet.core.ui.theme.UpYetTheme
import dev.upyet.reliability.domain.ReliabilitySummary
import dev.upyet.settings.data.AppLanguage
import dev.upyet.settings.data.AppSettings
import dev.upyet.settings.data.RetentionPolicy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUpcomingAlarmTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theRowShowsTheCurrentValue() {
        setContent(leadMinutes = 60)

        compose.onNodeWithText("Upcoming alarm").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 hour").assertIsDisplayed()
    }

    @Test fun pickingAnOptionCallsTheCallback() {
        var chosen: Int? = null
        setContent(leadMinutes = 60, onUpcoming = { chosen = it })

        compose.onNodeWithText("Upcoming alarm").performScrollTo().performClick()
        compose.onNodeWithText("Off").performClick()

        assertThat(chosen).isEqualTo(0)
    }

    private fun setContent(leadMinutes: Int, onUpcoming: (Int) -> Unit = {}) {
        compose.setContent {
            UpYetTheme {
                SettingsContent(
                    state = SettingsUiState(
                        settings = AppSettings(RetentionPolicy.SEVEN_DAYS, 9, true, true, 3, leadMinutes),
                        reliabilitySummary = ReliabilitySummary(blockedCount = 0, totalCount = 5),
                        language = AppLanguage.SYSTEM,
                        languageSupported = false,
                    ),
                    onReliability = {},
                    onUpcomingAlarmLead = onUpcoming,
                )
            }
        }
    }
}
