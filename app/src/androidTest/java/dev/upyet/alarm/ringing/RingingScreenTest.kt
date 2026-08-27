package dev.upyet.alarm.ringing

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
import dev.upyet.R
import dev.upyet.core.ui.theme.UpYetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RingingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsRemainAvailableWhenEvidenceFails() {
        compose.setContent {
            UpYetTheme {
                RingingScreen(RingingUiState(currentTime = LocalTime.of(7, 0), snoozeMinutes = 9), {}, {}) {
                    Text("Evidence unavailable", Modifier.padding(8.dp).semantics { contentDescription = "Evidence recording in progress" })
                }
            }
        }
        compose.onNodeWithText("Snooze 9 minutes").assertIsEnabled().assertHasClickAction()
        compose.onNodeWithContentDescription("Dismiss alarm").assertIsEnabled().assertHasClickAction()
        compose.onNodeWithContentDescription("Evidence recording in progress").assertIsDisplayed()
    }

    /**
     * The last ring of a snooze chain offers no Snooze at all rather than a disabled one: a greyed-out
     * button at 6am is a puzzle, not an affordance.
     */
    @Test fun theFinalRingOffersNoSnoozeAtAll() {
        show(RingingUiState(currentTime = LocalTime.of(7, 0), snoozeMinutes = 9, snoozeAllowed = false))

        compose.onNodeWithText("Last alarm — no snoozes left").assertIsDisplayed()
        compose.onNodeWithText("Snooze 9 minutes").assertDoesNotExist()
        compose.onNodeWithText("Last snooze").assertDoesNotExist()
    }

    /** Dismissal survives every other state on this screen, and the final ring is not an exception. */
    @Test fun theFinalRingIsStillDismissible() {
        show(RingingUiState(currentTime = LocalTime.of(7, 0), snoozeMinutes = 9, snoozeAllowed = false))

        compose.onNodeWithContentDescription("Dismiss alarm").assertIsEnabled().assertHasClickAction()
    }

    @Test fun theRingBeforeTheLastOneWarnsThatItIsTheLastSnooze() {
        show(RingingUiState(currentTime = LocalTime.of(7, 0), snoozeMinutes = 9, isLastSnooze = true))

        compose.onNodeWithText("Last snooze").assertIsEnabled().assertHasClickAction()
        compose.onNodeWithText("Snooze 9 minutes").assertDoesNotExist()
    }

    @Test fun anOrdinarySnoozeKeepsItsMinutesLabel() {
        show(RingingUiState(currentTime = LocalTime.of(7, 0), snoozeMinutes = 9))

        compose.onNodeWithText("Snooze 9 minutes").assertIsEnabled()
        compose.onNodeWithText("Last snooze").assertDoesNotExist()
    }

    /**
     * A stream at zero means the alarm made no sound at all. The screen has to say why it is buzzing
     * instead, or the user is left with an alarm that behaved inexplicably.
     */
    @Test fun aSilentAlarmStreamIsExplainedOnScreen() {
        show(RingingUiState(currentTime = LocalTime.of(7, 0), snoozeMinutes = 9, ringingNoteRes = R.string.ringing_volume_silent))

        compose.onNodeWithText("Alarm sound is off — vibrating instead").assertIsDisplayed()
    }

    @Test fun aVibrateOnlyAlarmIsExplainedOnScreen() {
        show(RingingUiState(currentTime = LocalTime.of(7, 0), snoozeMinutes = 9, ringingNoteRes = R.string.ringing_vibration_only))

        compose.onNodeWithText("Vibration only").assertIsDisplayed()
    }

    @Test fun anAudibleAlarmSaysNothingAboutVolume() {
        show(RingingUiState(currentTime = LocalTime.of(7, 0), snoozeMinutes = 9))

        compose.onNodeWithText("Alarm sound is off — vibrating instead").assertDoesNotExist()
    }

    private fun show(state: RingingUiState) {
        compose.setContent { UpYetTheme { RingingScreen(state, {}, {}) } }
    }
}
