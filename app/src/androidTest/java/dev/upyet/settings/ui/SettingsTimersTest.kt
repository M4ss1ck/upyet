package dev.upyet.settings.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import dev.upyet.core.ui.rememberCameraGate
import dev.upyet.core.ui.theme.UpYetTheme
import dev.upyet.reliability.domain.ReliabilitySummary
import dev.upyet.settings.data.AppLanguage
import dev.upyet.settings.data.AppSettings
import dev.upyet.settings.data.RetentionPolicy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTimersTest {
    @get:Rule val compose = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun timerSwitch() = compose.onNodeWithTag(TIMER_EVIDENCE_DEFAULT_SWITCH_TAG)

    @Test fun switchShowsTheStoredValue() {
        setContent(timerEvidenceByDefault = true)
        timerSwitch().performScrollTo().assertIsOn()
    }

    @Test fun switchShowsOffWhenTheStoredValueIsOff() {
        setContent(timerEvidenceByDefault = false)
        timerSwitch().performScrollTo().assertIsOff()
    }

    @Test fun turningOnWithoutCameraShowsTheDisclosureFirstAndWritesNothing() {
        val written = mutableListOf<Boolean>()
        setContent(timerEvidenceByDefault = false, cameraGranted = false, onTimerEvidence = { written += it })

        timerSwitch().performScrollTo().performClick()

        compose.onNodeWithText(string(R.string.permission_onboarding_explanation)).assertExists()
        assertThat(written).isEmpty()
    }

    @Test fun turningOnWithCameraWritesAtOnceWithNoDisclosure() {
        val written = mutableListOf<Boolean>()
        setContent(timerEvidenceByDefault = false, cameraGranted = true, onTimerEvidence = { written += it })

        timerSwitch().performScrollTo().performClick()

        assertThat(written).containsExactly(true).inOrder()
        compose.onNodeWithText(string(R.string.permission_onboarding_explanation)).assertDoesNotExist()
    }

    @Test fun turningOffNeverShowsTheDisclosure() {
        val written = mutableListOf<Boolean>()
        setContent(timerEvidenceByDefault = true, cameraGranted = false, onTimerEvidence = { written += it })

        timerSwitch().performScrollTo().performClick()

        assertThat(written).containsExactly(false).inOrder()
        compose.onNodeWithText(string(R.string.permission_onboarding_explanation)).assertDoesNotExist()
    }

    private fun setContent(timerEvidenceByDefault: Boolean, cameraGranted: Boolean = true, onTimerEvidence: (Boolean) -> Unit = {}) {
        compose.setContent {
            UpYetTheme {
                SettingsContent(
                    state = SettingsUiState(
                        settings = AppSettings(
                            retention = RetentionPolicy.SEVEN_DAYS,
                            defaultSnoozeMinutes = 9,
                            defaultVibrationEnabled = true,
                            evidenceEnabledByDefault = true,
                            maxSnoozes = 3,
                            timerEvidenceByDefault = timerEvidenceByDefault,
                        ),
                        reliabilitySummary = ReliabilitySummary(blockedCount = 0, totalCount = 5),
                        language = AppLanguage.SYSTEM,
                        languageSupported = false,
                    ),
                    onReliability = {},
                    onTimerEvidence = onTimerEvidence,
                    cameraGate = rememberCameraGate(isGranted = { cameraGranted }),
                )
            }
        }
    }
}
