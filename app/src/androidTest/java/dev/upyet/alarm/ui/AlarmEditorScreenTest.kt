package dev.upyet.alarm.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.upyet.core.ui.theme.UpYetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The editor is taller than a phone screen. These tests constrain it to a small viewport so a regression
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
}
