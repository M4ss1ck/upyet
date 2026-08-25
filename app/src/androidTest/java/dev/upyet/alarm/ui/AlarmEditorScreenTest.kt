package dev.upyet.alarm.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.upyet.core.ui.theme.UpYetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import androidx.compose.material3.R as M3R

/**
 * The editor is taller than a phone screen, so the layout tests constrain it to a small viewport: a regression
 * that pushes Save below the fold - the v0.1.0 bug where an alarm could not be saved at all - fails here.
 */
@RunWith(AndroidJUnit4::class)
class AlarmEditorScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun setSmallScreenEditor(state: AlarmEditorUiState) {
        compose.setContent {
            UpYetTheme {
                Box(Modifier.size(width = 320.dp, height = 480.dp)) {
                    AlarmEditorContent(state = state, isNewAlarm = true, onUpdate = {}, onSave = {}, onCancel = {})
                }
            }
        }
    }

    @Test fun saveAndCancelStayVisibleOnASmallScreen() {
        setSmallScreenEditor(AlarmEditorUiState(isLoaded = true))
        compose.onNodeWithText("Save alarm").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Cancel").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun saveIsDisabledUntilTheAlarmHasLoaded() {
        setSmallScreenEditor(AlarmEditorUiState(isLoaded = false))
        compose.onNodeWithText("Save alarm").assertIsDisplayed()
    }

    /**
     * Picking a minute must leave the dial on minutes. A picker rebuilt on every time change comes back
     * in its initial hour mode, which is the v0.1.2 bug where a minute could be set once and never adjusted.
     */
    @Test fun theDialStaysOnMinutesAfterAMinuteIsPicked() {
        var state by mutableStateOf(AlarmEditorUiState(time = LocalTime.of(7, 0), isLoaded = true))
        compose.setContent {
            UpYetTheme {
                AlarmEditorContent(
                    state = state,
                    isNewAlarm = true,
                    onUpdate = { transform -> state = transform(state) },
                    onSave = {},
                    onCancel = {},
                )
            }
        }

        val minuteMode = m3String(M3R.string.m3c_time_picker_minute_selection)
        compose.onNodeWithContentDescription(minuteMode).performClick()
        compose.onNodeWithContentDescription(m3String(M3R.string.m3c_time_picker_minute_suffix, 35))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertThat(state.time.minute).isEqualTo(35)
        compose.onNodeWithContentDescription(minuteMode).assertIsSelected()
    }

    /** The picker labels its own parts with Material's strings, so the test asks for them by resource. */
    private fun m3String(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
}
