package dev.upyet.history.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.upyet.core.ui.theme.UpYetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule val compose = createComposeRule()

    /**
     * The four filter labels are wider than a phone, so the row scrolls rather than squeezing the last
     * chip - which is how "Missed" ended up broken over two lines.
     */
    @Test fun theLastFilterChipKeepsItsLabelOnOneLine() {
        compose.setContent {
            UpYetTheme {
                Box(Modifier.size(width = 320.dp, height = 640.dp)) {
                    HistoryContent(HistoryUiState(), onFilterChange = {}, onOpen = {})
                }
            }
        }
        assertThat(labelHeight("Missed")).isEqualTo(labelHeight("All"))
    }

    private fun labelHeight(text: String): Dp = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().height
}
