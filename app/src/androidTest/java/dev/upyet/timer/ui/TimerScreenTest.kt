package dev.upyet.timer.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import dev.upyet.core.ui.theme.UpYetTheme
import dev.upyet.timer.domain.TimerId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TimerScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun string(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun row(id: Long, tag: TimerTag, label: String = "Row $id", evidence: Boolean = false, ringsAt: Instant? = null) =
        TimerRowState(
            id = TimerId(id),
            label = label,
            tag = tag,
            remaining = Duration.ofSeconds(300),
            length = Duration.ofSeconds(300),
            evidenceEnabled = evidence,
            ringsAt = ringsAt,
        )

    /** The locale-proof start of "Rings at %1$s · %2$s timer", taken from the resource itself. */
    private fun ringsAtPrefix(): String {
        val prefix = string(R.string.timer_rings_at_with_length, "@", "@").substringBefore("@").trim()
        assertThat(prefix).isNotEmpty()
        return prefix
    }

    private fun lengthText() = string(R.string.timer_length, formatTimerDuration(Duration.ofSeconds(300)))

    private fun show(state: TimerUiState, callbacks: TimerCallbacks = TimerCallbacks()) {
        compose.setContent { UpYetTheme { TimerContent(state, callbacks) } }
    }

    @Test fun presetClickReportsItsMinutes() {
        var preset = -1
        show(TimerUiState(), TimerCallbacks(onPreset = { preset = it }))
        compose.onNodeWithText("5 min").performClick()
        assertThat(preset).isEqualTo(5)
    }

    @Test fun scrollingTheMinutesWheelReportsTheCentredValue() {
        var minutes = -1
        show(TimerUiState(), TimerCallbacks(onMinutes = { minutes = it }))
        compose.onNodeWithTag("timer_wheel_minutes").performScrollToIndex(7)
        compose.waitForIdle()
        assertThat(minutes).isEqualTo(7)
    }

    @Test fun wheelFollowsAnExternalValueWithoutReportingBack() {
        var reported = -1
        compose.setContent {
            UpYetTheme { TimerContent(TimerUiState(minutes = 30), TimerCallbacks(onMinutes = { reported = it })) }
        }
        compose.waitForIdle()
        assertThat(reported).isEqualTo(-1)
        compose.onNodeWithTag("timer_wheel_minutes").assertIsDisplayed()
    }

    @Test fun startIsDisabledAtZeroAndEnabledWithALength() {
        show(TimerUiState())
        compose.onNodeWithText(string(R.string.timer_start)).assertIsNotEnabled()
    }

    @Test fun startClickRaisesOnStart() {
        var started = false
        show(TimerUiState(minutes = 5), TimerCallbacks(onStart = { started = true }))
        compose.onNodeWithText(string(R.string.timer_start)).assertIsEnabled().performClick()
        assertThat(started).isTrue()
    }

    @Test fun rowsAreRenderedWithTheirTags() {
        val rows = listOf(row(1, TimerTag.RINGING), row(2, TimerTag.RUNNING), row(3, TimerTag.PAUSED), row(4, TimerTag.IDLE))
        show(TimerUiState(timers = rows))
        listOf(R.string.timer_tag_ringing, R.string.timer_tag_running, R.string.timer_tag_paused, R.string.timer_tag_idle).forEach {
            compose.onNodeWithText(string(it)).assertExists()
        }
        compose.onNodeWithText("Row 1").assertExists()
    }

    @Test fun emptyListShowsTheEmptyState() {
        show(TimerUiState())
        compose.onNodeWithText(string(R.string.timer_empty)).assertExists()
    }

    @Test fun evidenceChipIsEnabledInIdleRunningAndPaused() {
        val tag = mutableStateOf(TimerTag.IDLE)
        compose.setContent { UpYetTheme { TimerContent(TimerUiState(timers = listOf(row(1, tag.value))), TimerCallbacks()) } }
        val off = string(R.string.timer_rec_off_description)
        listOf(TimerTag.IDLE, TimerTag.RUNNING, TimerTag.PAUSED).forEach {
            tag.value = it
            compose.onNodeWithContentDescription(off).assertIsEnabled()
        }
    }

    @Test fun evidenceChipIsDisabledWhileRinging() {
        show(TimerUiState(timers = listOf(row(1, TimerTag.RINGING))))
        compose.onNodeWithContentDescription(string(R.string.timer_rec_off_description)).assertIsNotEnabled()
    }

    @Test fun missedRowOffersOnlyAddMinuteResetAndDelete() {
        show(TimerUiState(timers = listOf(row(1, TimerTag.MISSED))))
        compose.onNodeWithText(string(R.string.timer_add_minute)).assertExists()
        compose.onNodeWithContentDescription(string(R.string.timer_reset)).assertExists()
        compose.onNodeWithContentDescription(string(R.string.timer_delete)).assertExists()
        compose.onNodeWithContentDescription(string(R.string.timer_start)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.timer_pause)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.timer_resume)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.timer_rec_off_description)).assertDoesNotExist()
    }

    @Test fun idleRowHasNoAddMinute() {
        show(TimerUiState(timers = listOf(row(1, TimerTag.IDLE))))
        compose.onNodeWithText(string(R.string.timer_add_minute)).assertDoesNotExist()
    }

    @Test fun rowControlsRaiseTheirCallbacks() {
        val calls = mutableListOf<String>()
        val callbacks = TimerCallbacks(
            onPause = { calls += "pause ${it.value}" },
            onAddMinute = { calls += "add ${it.value}" },
            onEvidence = { id, on -> calls += "rec ${id.value} $on" },
        )
        show(TimerUiState(timers = listOf(row(7, TimerTag.RUNNING))), callbacks)
        compose.onNodeWithContentDescription(string(R.string.timer_pause)).performClick()
        compose.onNodeWithText(string(R.string.timer_add_minute)).performClick()
        compose.onNodeWithContentDescription(string(R.string.timer_rec_off_description)).performClick()
        assertThat(calls).containsExactly("pause 7", "add 7", "rec 7 true").inOrder()
    }

    @Test fun runningRowShowsItsRingTimeInPlaceOfTheLength() {
        show(TimerUiState(timers = listOf(row(1, TimerTag.RUNNING, ringsAt = Instant.parse("2026-10-27T10:00:00Z")))))
        compose.onNode(hasText(ringsAtPrefix(), substring = true)).assertExists()
        compose.onNodeWithText(lengthText()).assertDoesNotExist()
    }

    @Test fun pausedRowShowsNoRingTimeAndTheLength() {
        show(TimerUiState(timers = listOf(row(1, TimerTag.PAUSED))))
        compose.onNode(hasText(ringsAtPrefix(), substring = true)).assertDoesNotExist()
        compose.onNodeWithText(lengthText()).assertExists()
    }

    @Test fun addMinuteIsAButtonThatRaisesOnAddMinuteWithTheRowId() {
        var added = -1L
        show(
            TimerUiState(timers = listOf(row(9, TimerTag.RUNNING))),
            TimerCallbacks(onAddMinute = { added = it.value }),
        )
        val button = compose.onNodeWithContentDescription(string(R.string.timer_add_minute_description))
        button.assertHasClickAction().performClick()
        assertThat(added).isEqualTo(9L)
    }

    @Test fun addMinuteButtonIsAtLeastATouchTargetHigh() {
        show(TimerUiState(timers = listOf(row(1, TimerTag.RUNNING))))
        compose.onNodeWithContentDescription(string(R.string.timer_add_minute_description)).assertHeightIsAtLeast(48.dp)
    }
}
