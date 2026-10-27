package dev.upyet.stopwatch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import dev.upyet.core.ui.theme.UpYetTheme
import dev.upyet.stopwatch.domain.Stopwatch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class StopwatchScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun string(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun show(stopwatch: Stopwatch, elapsed: Duration, callbacks: StopwatchCallbacks = StopwatchCallbacks()) {
        compose.setContent {
            UpYetTheme { StopwatchContent(state = StopwatchUiState(stopwatch), elapsed = elapsed, callbacks = callbacks) }
        }
    }

    @Test fun idleShowsZeroStartAndDisablesLapAndReset() {
        show(Stopwatch.IDLE, Duration.ZERO)
        compose.onNodeWithTag("stopwatch_time").assertIsDisplayed().assert(hasText("00:00.00"))
        compose.onNodeWithContentDescription(string(R.string.stopwatch_start)).assertIsEnabled()
        compose.onNodeWithTag("stopwatch_lap").assertIsNotEnabled()
        compose.onNodeWithTag("stopwatch_reset").assertIsNotEnabled()
    }

    @Test fun runningShowsPauseEnablesLapAndReset() {
        show(Stopwatch(runningSince = Duration.ZERO, bootCount = 1), Duration.ofSeconds(3))
        compose.onNodeWithTag("stopwatch_time").assert(hasText("00:03.00"))
        compose.onNodeWithContentDescription(string(R.string.stopwatch_pause)).assertIsEnabled()
        compose.onNodeWithTag("stopwatch_lap").assertIsEnabled()
        compose.onNodeWithTag("stopwatch_reset").assertIsEnabled()
    }

    @Test fun runningButtonsInvokeTheirCallbacks() {
        var laps = 0
        var pauses = 0
        var resets = 0
        var starts = 0
        show(
            Stopwatch(runningSince = Duration.ZERO, bootCount = 1),
            Duration.ZERO,
            StopwatchCallbacks(
                onStart = { starts++ },
                onPause = { pauses++ },
                onLap = { laps++ },
                onReset = { resets++ },
            ),
        )
        compose.onNodeWithTag("stopwatch_lap").performClick()
        compose.onNodeWithTag("stopwatch_start_pause").performClick()
        compose.onNodeWithTag("stopwatch_reset").performClick()
        compose.waitForIdle()
        assertThat(laps).isEqualTo(1)
        assertThat(pauses).isEqualTo(1)
        assertThat(resets).isEqualTo(1)
        assertThat(starts).isEqualTo(0)
    }

    @Test fun pausedWithTimeOffersResumeAndReset() {
        var starts = 0
        show(
            Stopwatch(accumulated = Duration.ofSeconds(5)),
            Duration.ofSeconds(5),
            StopwatchCallbacks(onStart = { starts++ }),
        )
        compose.onNodeWithContentDescription(string(R.string.stopwatch_resume)).assertIsEnabled().performClick()
        compose.onNodeWithTag("stopwatch_reset").assertIsEnabled()
        compose.onNodeWithTag("stopwatch_lap").assertIsNotEnabled()
        compose.waitForIdle()
        assertThat(starts).isEqualTo(1)
    }

    @Test fun lapRowsShowNumberSplitAndTotal() {
        val laps = listOf(10L, 25L, 45L).map { Duration.ofSeconds(it) }
        show(Stopwatch(accumulated = Duration.ofSeconds(45), laps = laps), Duration.ofSeconds(45))
        compose.onNodeWithTag("stopwatch_lap_row_1").assertIsDisplayed()
        compose.onNodeWithTag("stopwatch_lap_row_3").assertIsDisplayed()
        compose.onNodeWithTag("stopwatch_lap_row_1").assert(hasAnyDescendant(hasText(string(R.string.stopwatch_lap_number, 1))))
        compose.onNodeWithTag("stopwatch_lap_row_1").assert(hasAnyDescendant(hasText("00:10.00")))
        compose.onNodeWithTag("stopwatch_lap_row_2").assert(hasAnyDescendant(hasText("00:15.00")))
        compose.onNodeWithTag("stopwatch_lap_row_3").assert(hasAnyDescendant(hasText("00:20.00")))
        compose.onNodeWithTag("stopwatch_lap_row_3").assert(hasAnyDescendant(hasText("00:45.00")))
    }

    @Test fun manyLapsScrollTheListAndNotTheScreen() {
        val laps = (1..40).map { Duration.ofSeconds(it * 10L) }
        show(Stopwatch(accumulated = Duration.ofSeconds(400), laps = laps), Duration.ofSeconds(400))
        compose.onNodeWithTag("stopwatch_lap_row_1").assertIsDisplayed()
        compose.onNodeWithTag("stopwatch_laps").performScrollToIndex(39)
        compose.waitForIdle()
        compose.onNodeWithTag("stopwatch_lap_row_40").assertIsDisplayed().assert(hasAnyDescendant(hasText("06:40.00")))
        compose.onNodeWithTag("stopwatch_lap_row_1").assertDoesNotExist()
        compose.onNodeWithTag("stopwatch_time").assertIsDisplayed()
    }

    @Test fun dialTakesEightyEightPercentOfAPortraitPhoneWidth() {
        showIn(width = 400.dp, height = 900.dp)
        compose.onNodeWithTag("stopwatch_dial").assertWidthIsEqualTo(352.dp).assertHeightIsEqualTo(352.dp)
    }

    @Test fun dialIsCappedByHeightInALandscapeWindow() {
        showIn(width = 800.dp, height = 400.dp)
        compose.onNodeWithTag("stopwatch_dial").assertWidthIsEqualTo(180.dp)
        compose.onNodeWithTag("stopwatch_start_pause").assertIsDisplayed()
    }

    private fun showIn(width: Dp, height: Dp) {
        compose.setContent {
            UpYetTheme {
                Box(Modifier.size(width, height)) {
                    StopwatchContent(state = StopwatchUiState(Stopwatch.IDLE), elapsed = Duration.ZERO, callbacks = StopwatchCallbacks())
                }
            }
        }
    }
}
