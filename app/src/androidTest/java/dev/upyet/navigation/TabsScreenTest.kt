package dev.upyet.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import dev.upyet.core.ui.components.RELIABILITY_DOT_TAG
import dev.upyet.core.ui.theme.UpYetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()

    @Test fun startsOnAlarms() {
        setShell()
        compose.onNodeWithText("content ALARMS").assertExists()
        navItem("Alarms").assertIsSelected()
        headline("Alarms").assertIsDisplayed()
    }

    @Test fun tappingEachTabShowsItsContentAndHeadline() {
        setShell()
        listOf("World clock" to "WORLD_CLOCK", "Timer" to "TIMER", "Stopwatch" to "STOPWATCH").forEach { (label, name) ->
            navItem(label).performClick()
            compose.onNodeWithText("content $name").assertExists()
            navItem(label).assertIsSelected()
            headline(label).assertIsDisplayed()
        }
    }

    @Test fun swipingDoesNotChangeTab() {
        setShell()
        compose.onNodeWithText("content ALARMS").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("content ALARMS").assertExists()
        navItem("Alarms").assertIsSelected()
    }

    @Test fun swipingRightOpensTheDrawerOnAlarms() {
        setShell()
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("History").assertIsDisplayed()
    }

    /** A start-to-end swipe removes a world clock row; it must not open the drawer as well. */
    @Test fun swipingRightDoesNotOpenTheDrawerOnWorldClock() {
        setShell()
        navItem("World clock").performClick()
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("History").assertIsNotDisplayed()
        openDrawer()
        compose.onNodeWithText("History").assertIsDisplayed()
    }

    @Test fun hamburgerOpensDrawerWithThreeDestinations() {
        setShell()
        openDrawer()
        compose.onNodeWithText("History").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Reliability").assertIsDisplayed()
        compose.onNodeWithText(activityString(R.string.drawer_tagline)).assertIsDisplayed()
    }

    @Test fun historyItemInvokesOnHistoryAndClosesDrawer() {
        setShell()
        openDrawer()
        compose.onNodeWithText("History").performClick()
        compose.waitForIdle()
        assertThat(calls).containsExactly("history")
        assertNotShown("History")
    }

    @Test fun settingsItemInvokesOnSettingsAndClosesDrawer() {
        setShell()
        openDrawer()
        compose.onNodeWithText("Settings").performClick()
        compose.waitForIdle()
        assertThat(calls).containsExactly("settings")
        assertNotShown("Settings")
    }

    @Test fun reliabilityItemInvokesOnReliabilityAndClosesDrawer() {
        setShell()
        openDrawer()
        compose.onNodeWithText("Reliability").performClick()
        compose.waitForIdle()
        assertThat(calls).containsExactly("reliability")
        assertNotShown("Reliability")
    }

    @Test fun dotAndBadgeShownWhenActionNeeded() {
        setShell(actionNeeded = true)
        compose.onNodeWithTag(RELIABILITY_DOT_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription(activityString(R.string.open_menu_action_needed)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(activityString(R.string.action_needed)).assertIsDisplayed()
    }

    @Test fun noDotOrBadgeWhenAllGood() {
        setShell(actionNeeded = false)
        compose.onNodeWithTag(RELIABILITY_DOT_TAG, useUnmergedTree = true).assertDoesNotExist()
        openDrawer()
        compose.onAllNodesWithText(activityString(R.string.action_needed)).assertCountEquals(0)
    }

    @Test fun backFromClockToolReturnsToAlarms() {
        setShell()
        navItem("Timer").performClick()
        compose.onNodeWithText("content TIMER").assertExists()
        pressBack()
        compose.onNodeWithText("content ALARMS").assertExists()
        navItem("Alarms").assertIsSelected()
    }

    @Test fun backWithDrawerOpenClosesIt() {
        setShell()
        openDrawer()
        pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("content ALARMS").assertExists()
        assertNotShown("History")
    }

    @Test fun backOnAlarmsIsNotConsumed() {
        setShell()
        val enabled = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.hasEnabledCallbacks() }
        assertThat(enabled).isFalse()
    }

    private fun setShell(actionNeeded: Boolean = false) {
        compose.setContent {
            UpYetTheme {
                ClockShell(
                    actionNeeded = actionNeeded,
                    onHistory = { calls += "history" },
                    onSettings = { calls += "settings" },
                    onReliability = { calls += "reliability" },
                ) { tab ->
                    Text("content ${tab.name}")
                }
            }
        }
    }

    /** The bottom bar item: the only selectable node carrying this label. */
    private fun navItem(label: String) = compose.onNode(hasText(label) and isSelectable())

    /** The headline: the same label, but as a plain text node rather than a selectable tab. */
    private fun headline(label: String) = compose.onNode(hasText(label) and !isSelectable())

    private fun openDrawer() {
        compose.onNodeWithContentDescription(activityString(R.string.open_menu)).performClick()
        compose.waitForIdle()
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /**
     * A closed drawer may keep its items composed off-screen, so "not shown" means the item is not displayed,
     * whether or not the node exists at all.
     */
    private fun assertNotShown(text: String) {
        val displayed = runCatching { compose.onNodeWithText(text).assertIsDisplayed() }.isSuccess
        assertThat(displayed).isFalse()
    }

    private fun activityString(id: Int): String = compose.activity.getString(id)
}
