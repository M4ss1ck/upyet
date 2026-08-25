package dev.upyet.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.upyet.core.ui.theme.UpYetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun swipingLeftMovesToTheNextTabAndUpdatesTheBar() {
        showTabs()
        compose.onNodeWithText("page 0").performTouchInput { swipeLeft() }
        compose.onNodeWithText("page 1").assertExists()
        compose.onNodeWithText("History").assertIsSelected()
    }

    @Test fun swipingRightOnTheFirstTabStaysThere() {
        showTabs()
        compose.onNodeWithText("page 0").performTouchInput { swipeRight() }
        compose.onNodeWithText("page 0").assertExists()
        compose.onNodeWithText("Alarms").assertIsSelected()
    }

    private fun showTabs() {
        compose.setContent {
            UpYetTheme {
                TabsScaffold(rememberPagerState(pageCount = { tabs.size })) { page ->
                    Text("page $page", Modifier.fillMaxSize())
                }
            }
        }
    }
}
