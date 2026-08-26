package dev.upyet.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.SnoozeBudget
import dev.upyet.core.ui.theme.UpYetTheme
import dev.upyet.reliability.domain.ReliabilitySummary
import dev.upyet.settings.data.AppLanguage
import dev.upyet.settings.data.AppSettings
import dev.upyet.settings.data.RetentionPolicy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The setting that decides how long a snooze chain can run, and whether it ever ends on its own. */
@RunWith(AndroidJUnit4::class)
class SettingsMaxSnoozesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theRowShowsTheCurrentCap() {
        setContent(maxSnoozes = 3)

        compose.onNodeWithText("Maximum snoozes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 snoozes").assertIsDisplayed()
    }

    @Test fun aCapOfOneReadsInTheSingular() {
        setContent(maxSnoozes = 1)

        compose.onNodeWithText("1 snooze").performScrollTo().assertIsDisplayed()
    }

    /** Unlimited is not a number, and rendering it as "-1 snoozes" is the obvious way to get this wrong. */
    @Test fun anUnlimitedCapReadsAsUnlimited() {
        setContent(maxSnoozes = SnoozeBudget.UNLIMITED)

        compose.onNodeWithText("Maximum snoozes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unlimited").assertIsDisplayed()
        compose.onNodeWithText("-1 snoozes").assertDoesNotExist()
    }

    @Test fun choosingACapReportsIt() {
        var chosen: Int? = null
        setContent(maxSnoozes = 3, onMaxSnoozes = { chosen = it })

        compose.onNodeWithText("Maximum snoozes").performScrollTo().performClick()
        compose.onNodeWithText("5 snoozes").performClick()

        assertThat(chosen).isEqualTo(5)
    }

    @Test fun unlimitedCanBeChosen() {
        var chosen: Int? = null
        setContent(maxSnoozes = 3, onMaxSnoozes = { chosen = it })

        compose.onNodeWithText("Maximum snoozes").performScrollTo().performClick()
        compose.onNodeWithText("Unlimited").performClick()

        assertThat(chosen).isEqualTo(SnoozeBudget.UNLIMITED)
    }

    private fun setContent(maxSnoozes: Int, onMaxSnoozes: (Int) -> Unit = {}) {
        compose.setContent {
            UpYetTheme {
                SettingsContent(
                    state = SettingsUiState(
                        settings = AppSettings(RetentionPolicy.SEVEN_DAYS, 9, true, true, maxSnoozes),
                        reliabilitySummary = ReliabilitySummary(blockedCount = 0, totalCount = 5),
                        language = AppLanguage.SYSTEM,
                        languageSupported = false,
                    ),
                    onReliability = {},
                    onMaxSnoozes = onMaxSnoozes,
                )
            }
        }
    }
}
