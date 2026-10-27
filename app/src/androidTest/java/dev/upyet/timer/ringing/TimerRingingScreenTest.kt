package dev.upyet.timer.ringing

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.upyet.core.ui.theme.UpYetTheme
import dev.upyet.evidence.camera.EvidenceRecordingState
import dev.upyet.evidence.domain.EvidenceErrorCode
import dev.upyet.evidence.domain.EvidenceStatus
import dev.upyet.timer.domain.TimerId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TimerRingingScreenTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    /** Every camera state the screen must keep its three controls under (AGENTS.md §9a). */
    private val cameraStates: List<EvidenceRecordingState> = listOf(
        EvidenceRecordingState.Unavailable(EvidenceStatus.FAILED, EvidenceErrorCode.CAMERA_UNAVAILABLE),
        EvidenceRecordingState.Unavailable(EvidenceStatus.FAILED, EvidenceErrorCode.CAMERA_IN_USE),
        EvidenceRecordingState.Unavailable(EvidenceStatus.SKIPPED, EvidenceErrorCode.PERMISSION_DENIED),
        EvidenceRecordingState.Unavailable(EvidenceStatus.SKIPPED, EvidenceErrorCode.DIRECT_BOOT_UNAVAILABLE),
        EvidenceRecordingState.Recording(Instant.EPOCH, 65_000L),
        EvidenceRecordingState.Preparing,
    )

    @Test fun controlsWorkWhileTheCameraIsUnavailable() {
        val evidence = mutableStateOf(cameraStates.first())
        compose.setContent {
            UpYetTheme {
                RingingUnderTest(state = twoRinging(), evidence = evidence.value)
            }
        }

        for (state in cameraStates) {
            evidence.value = state
            compose.waitForIdle()
            calls.clear()

            compose.onNodeWithText("Stop").assertIsDisplayed().assertIsEnabled().assertHasClickAction().performClick()
            compose.onNodeWithContentDescription("Add one minute").assertIsDisplayed().assertIsEnabled().assertHasClickAction()
                .performClick()
            compose.onNodeWithText("Stop all").assertIsDisplayed().assertIsEnabled().assertHasClickAction().performClick()

            assertWithMessage("callbacks under $state").that(calls).containsExactly("stop:1", "addMinute:1", "stopAll").inOrder()
        }
    }

    @Test fun theCameraFailureBadgeSaysCameraUnavailableForEveryCameraErrorCode() {
        val evidence = mutableStateOf<EvidenceRecordingState>(
            EvidenceRecordingState.Unavailable(EvidenceStatus.FAILED, EvidenceErrorCode.CAMERA_UNAVAILABLE),
        )
        compose.setContent { UpYetTheme { RingingUnderTest(state = twoRinging(recordingOn = true), evidence = evidence.value) } }

        for (code in listOf(
            EvidenceErrorCode.CAMERA_UNAVAILABLE,
            EvidenceErrorCode.CAMERA_IN_USE,
            EvidenceErrorCode.INITIALIZATION_FAILED,
        )) {
            evidence.value = EvidenceRecordingState.Unavailable(EvidenceStatus.FAILED, code)
            compose.waitForIdle()
            compose.onNodeWithText("Not recording · camera unavailable").assertIsDisplayed()
        }
    }

    @Test fun theBadgeSaysNotRecordingUntilFirstUnlockBeforeTheUserUnlocks() {
        show(evidence = EvidenceRecordingState.Unavailable(EvidenceStatus.SKIPPED, EvidenceErrorCode.DIRECT_BOOT_UNAVAILABLE))

        compose.onNodeWithText("Not recording until first unlock").assertIsDisplayed()
    }

    @Test fun theBadgeSaysCameraPermissionIsOffWhenItIsDenied() {
        show(evidence = EvidenceRecordingState.Unavailable(EvidenceStatus.SKIPPED, EvidenceErrorCode.PERMISSION_DENIED))

        compose.onNodeWithText("Not recording · camera permission off").assertIsDisplayed()
    }

    @Test fun theRecordingBadgeNamesTheStateAndElapsedTimeNotOnlyItsColour() {
        show(evidence = EvidenceRecordingState.Recording(Instant.EPOCH, 65_000L))

        compose.onNodeWithText("Recording evidence · 1:05").assertIsDisplayed()
        compose.onNodeWithContentDescription("Evidence recording in progress").assertIsDisplayed()
    }

    /** With every ringing timer on Timer evidence off, the screen says nothing about the camera at all. */
    @Test fun noBadgeAppearsWhenNoRingingTimerHasEvidenceOn() {
        show(
            state = twoRinging(recordingOn = false),
            evidence = EvidenceRecordingState.Unavailable(EvidenceStatus.FAILED, EvidenceErrorCode.CAMERA_UNAVAILABLE),
        )

        compose.onNodeWithText("Not recording · camera unavailable").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show camera preview").assertDoesNotExist()
        compose.onNodeWithContentDescription("Evidence recording in progress").assertDoesNotExist()
    }

    @Test fun tappingAQueueChipMovesTheFocusToThatTimer() {
        show()

        compose.onNodeWithText("Pasta · −0:45").performClick()

        assertThat(calls).containsExactly("focus:2")
    }

    @Test fun stopAllIsAbsentWithASingleRingingTimer() {
        show(state = TimerRingingUiState(phase = TimerRingingPhase.RINGING, focused = eggs, queue = emptyList()))

        compose.onNodeWithText("Stop all").assertDoesNotExist()
        compose.onNodeWithText("Stop").assertIsDisplayed()
    }

    @Test fun theLockGlyphShowsOverTheKeyguard() {
        show(locked = true)

        compose.onNodeWithContentDescription("Phone locked").assertIsDisplayed()
    }

    @Test fun theLockGlyphIsAbsentWhenTheDeviceIsUnlocked() {
        show(locked = false)

        compose.onNodeWithContentDescription("Phone locked").assertDoesNotExist()
    }

    /** The lock screen offers no way to unlock from here: the ringing screen never dismisses the keyguard (AGENTS.md §7). */
    @Test fun noUnlockActionAppearsOverTheKeyguard() {
        show(locked = true)

        compose.onAllNodesWithText("Unlock", substring = true, ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Unlock", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test fun thePreviewToggleFiresItsCallback() {
        show(previewVisible = false)

        compose.onNodeWithContentDescription("Show camera preview").assertIsDisplayed().performClick()

        assertThat(calls).containsExactly("togglePreview")
    }

    /**
     * Regression, found by the 0.3.0 release gate: the slot is also the camera's preview surface, and CameraX starts no
     * capture session until every bound use case has one. A slot left out of the composition while the preview was
     * hidden (the default) made every timer recording finalize with no data, so it must be composed, told it is
     * hidden, whenever a timer records.
     */
    @Test fun theSlotIsComposedHiddenWhileRecordingWithThePreviewOff() {
        show(previewVisible = false, preview = { visible -> Text(if (visible) "preview-shown" else "preview-hidden") })

        compose.onNodeWithText("preview-hidden").assertExists()
        compose.onNodeWithText("preview-shown").assertDoesNotExist()
    }

    /** The slot is told it is visible only while turned on; with no timer recording it is not composed at all. */
    @Test fun thePreviewSlotShowsOnlyWhileVisibleAndRecording() {
        val state = mutableStateOf(twoRinging(recordingOn = true))
        compose.setContent {
            UpYetTheme {
                RingingUnderTest(
                    state = state.value,
                    evidence = EvidenceRecordingState.Recording(Instant.EPOCH, 65_000L),
                    previewVisible = true,
                ) { visible ->
                    Text(if (visible) "preview-shown" else "preview-hidden")
                }
            }
        }

        compose.onNodeWithText("preview-shown").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide camera preview").assertIsDisplayed()

        state.value = twoRinging(recordingOn = false)
        compose.waitForIdle()

        compose.onNodeWithText("preview-shown").assertDoesNotExist()
        compose.onNodeWithText("preview-hidden").assertDoesNotExist()
    }

    @Test fun aBlankLabelShowsTimerAsTheTitleAndNoLengthLineWhenLengthIsUnknown() {
        val unlabelled = RingingTimerItem(TimerId(1), "", null, Duration.ofSeconds(10))
        show(state = TimerRingingUiState(phase = TimerRingingPhase.RINGING, focused = unlabelled))

        compose.onNodeWithText("Timer").assertIsDisplayed()
        compose.onNodeWithText("9:00 timer").assertDoesNotExist()
        compose.onAllNodesWithText("timer", substring = true, ignoreCase = true).assertCountEquals(1)
    }

    private val eggs = RingingTimerItem(TimerId(1), "Eggs", Duration.ofMinutes(9), Duration.ofSeconds(56))
    private val pasta = RingingTimerItem(TimerId(2), "Pasta", Duration.ofMinutes(5), Duration.ofSeconds(45))

    private fun twoRinging(recordingOn: Boolean = true) =
        TimerRingingUiState(phase = TimerRingingPhase.RINGING, focused = eggs, queue = listOf(pasta), recordingOn = recordingOn)

    private fun show(
        state: TimerRingingUiState = twoRinging(),
        evidence: EvidenceRecordingState = EvidenceRecordingState.Recording(Instant.EPOCH, 65_000L),
        locked: Boolean = false,
        previewVisible: Boolean = false,
        preview: @Composable (visible: Boolean) -> Unit = {},
    ) {
        compose.setContent {
            UpYetTheme {
                RingingUnderTest(state, evidence, locked, previewVisible, preview)
            }
        }
    }

    @Composable
    private fun RingingUnderTest(
        state: TimerRingingUiState,
        evidence: EvidenceRecordingState,
        locked: Boolean = false,
        previewVisible: Boolean = false,
        preview: @Composable (visible: Boolean) -> Unit = {},
    ) {
        TimerRingingScreen(
            state = state,
            locked = locked,
            evidenceState = evidence,
            previewVisible = previewVisible,
            onTogglePreview = { calls.add("togglePreview") },
            onFocus = { calls.add("focus:${it.value}") },
            onStop = { calls.add("stop:${it.value}") },
            onAddMinute = { calls.add("addMinute:${it.value}") },
            onStopAll = { calls.add("stopAll") },
            preview = preview,
        )
    }
}
