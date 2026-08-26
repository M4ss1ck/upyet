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

/**
 * The per-app language override is an Android 13 framework feature. Below it the row must not appear at
 * all, because there is nothing behind it to change.
 */
@RunWith(AndroidJUnit4::class)
class SettingsLanguageTest {
    @get:Rule val compose = createComposeRule()

    private fun state(language: AppLanguage, languageSupported: Boolean) = SettingsUiState(
        settings = AppSettings(RetentionPolicy.SEVEN_DAYS, 9, true, true, SnoozeBudget.DEFAULT_MAX),
        reliabilitySummary = ReliabilitySummary(blockedCount = 0, totalCount = 5),
        language = language,
        languageSupported = languageSupported,
    )

    private fun setContent(state: SettingsUiState, onLanguage: (AppLanguage) -> Unit = {}) {
        compose.setContent {
            UpYetTheme { SettingsContent(state = state, onReliability = {}, onLanguage = onLanguage) }
        }
    }

    @Test fun theRowShowsTheLanguageCurrentlyInUse() {
        setContent(state(AppLanguage.SPANISH, languageSupported = true))
        compose.onNodeWithText("Language").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Español").assertIsDisplayed()
    }

    @Test fun choosingALanguageReportsIt() {
        var chosen: AppLanguage? = null
        setContent(state(AppLanguage.SYSTEM, languageSupported = true)) { chosen = it }

        compose.onNodeWithText("Language").performScrollTo().performClick()
        compose.onNodeWithText("English").performClick()
        compose.waitForIdle()

        assertThat(chosen).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test fun theRowIsAbsentWhenThePlatformHasNoOverride() {
        setContent(state(AppLanguage.SYSTEM, languageSupported = false))
        compose.onNodeWithText("Language").assertDoesNotExist()
    }
}
