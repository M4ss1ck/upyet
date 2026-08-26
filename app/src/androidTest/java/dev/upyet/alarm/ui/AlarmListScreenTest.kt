package dev.upyet.alarm.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.core.ui.theme.UpYetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@RunWith(AndroidJUnit4::class)
class AlarmListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rendersAlarmTimeAndLabel() {
        val time = LocalTime.of(7, 30)
        val alarm = Alarm(AlarmId(1), time, true, "Wake up", Recurrence.OneTime, null, true, 9, false, Instant.EPOCH, Instant.EPOCH)
        compose.setContent { UpYetTheme { AlarmListContent(AlarmListUiState(listOf(alarm)), {}, {}) } }
        compose.onNodeWithText(shortTime(time)).assertIsDisplayed()
        compose.onNodeWithText("Wake up").assertIsDisplayed()
    }

    /**
     * The expected time is formatted, never written out. A literal "7:30 AM" passes only on the exact
     * locale and ICU version it was typed against: ICU 72, which shipped in Android 14, changed the space
     * before the day period from U+0020 to U+202F, so the hardcoded string stopped matching what the app
     * correctly renders. Formatting the expectation the way `ClockText` does keeps this test about whether
     * the alarm's time reaches the screen, which is what it is for.
     */
    private fun shortTime(time: LocalTime): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0])
        .format(time)
}
