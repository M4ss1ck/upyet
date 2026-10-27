package dev.upyet.settings.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import dev.upyet.alarm.domain.SnoozePolicy
import dev.upyet.core.ui.theme.UpYetTheme
import dev.upyet.reliability.domain.ReliabilitySummary
import dev.upyet.settings.data.AppLanguage
import dev.upyet.settings.data.AppSettings
import dev.upyet.settings.data.RetentionPolicy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataSourcesScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun setContent(onBack: () -> Unit = {}) {
        compose.setContent {
            UpYetTheme { DataSourcesScreen(onBack = onBack) }
        }
    }

    @Test fun namesEverySource() {
        setContent()
        compose.onNodeWithText("Place data © GeoNames", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Made with Natural Earth", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("IANA Time Zone Database", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun showsEveryLicence() {
        setContent()
        compose.onNodeWithText("CC BY 4.0", substring = true).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Public domain", substring = true).assertCountEquals(2)
    }

    @Test fun showsThePrimaryLinks() {
        setContent()
        compose.onNodeWithText("https://www.geonames.org/", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("https://www.naturalearthdata.com/about/terms-of-use/", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("https://www.iana.org/time-zones", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun backInvokesOnBack() {
        var backs = 0
        setContent(onBack = { backs++ })
        compose.onNodeWithContentDescription(string(R.string.back)).performClick()
        assertThat(backs).isEqualTo(1)
    }

    @Test fun settingsRowOpensDataSources() {
        var opened = 0
        compose.setContent {
            UpYetTheme {
                SettingsContent(
                    state = SettingsUiState(
                        settings = AppSettings(RetentionPolicy.SEVEN_DAYS, SnoozePolicy.After(9), true, true, 3),
                        reliabilitySummary = ReliabilitySummary(blockedCount = 0, totalCount = 5),
                        language = AppLanguage.SYSTEM,
                        languageSupported = false,
                    ),
                    onReliability = {},
                    onDataSources = { opened++ },
                )
            }
        }
        compose.onNodeWithText(string(R.string.settings_data_sources)).performScrollTo().performClick()
        assertThat(opened).isEqualTo(1)
    }
}
