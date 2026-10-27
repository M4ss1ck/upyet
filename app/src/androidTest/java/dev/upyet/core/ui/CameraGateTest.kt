package dev.upyet.core.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import dev.upyet.core.ui.theme.UpYetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraGateTest {
    @get:Rule val compose = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun host(granted: Boolean, onRan: () -> Unit) {
        compose.setContent {
            UpYetTheme {
                val gate = rememberCameraGate(isGranted = { granted })
                CameraGateHost(gate)
                Button(onClick = { gate.whenGranted(onRan) }) { Text("turn on") }
            }
        }
    }

    @Test fun withoutPermissionTheDisclosureComesFirstAndTheActionWaits() {
        var ran = false
        host(granted = false) { ran = true }
        compose.onNodeWithText("turn on").performClick()
        compose.onNodeWithText(string(R.string.permission_onboarding_explanation)).assertExists()
        assertThat(ran).isFalse()
    }

    @Test fun notNowDismissesTheDisclosureAndNeverRunsTheAction() {
        var ran = false
        host(granted = false) { ran = true }
        compose.onNodeWithText("turn on").performClick()
        compose.onNodeWithText(string(R.string.camera_disclosure_dismiss)).performClick()
        compose.onNodeWithText(string(R.string.permission_onboarding_explanation)).assertDoesNotExist()
        assertThat(ran).isFalse()
    }

    @Test fun withPermissionTheActionRunsAtOnceWithNoDisclosure() {
        var ran = false
        host(granted = true) { ran = true }
        compose.onNodeWithText("turn on").performClick()
        assertThat(ran).isTrue()
        compose.onNodeWithText(string(R.string.permission_onboarding_explanation)).assertDoesNotExist()
    }
}
