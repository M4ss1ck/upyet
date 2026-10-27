package dev.upyet.worldclock.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.upyet.R
import dev.upyet.core.ui.theme.UpYetTheme
import dev.upyet.worldclock.data.WorldCity
import dev.upyet.worldclock.domain.WorldClockRow
import dev.upyet.worldclock.domain.worldClockRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class WorldClockContentTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale: Locale get() = context.resources.configuration.locales[0]

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun city(id: Long, name: String, iso: String, zone: String) =
        WorldCity(name, name, iso, ZoneId.of(zone), 0.0, 0.0, 1_000_000L, id)

    private val tokyo = city(1, "Tokyo", "JP", "Asia/Tokyo")
    private val losAngeles = city(2, "Los Angeles", "US", "America/Los_Angeles")
    private val mumbai = city(3, "Mumbai", "IN", "Asia/Kolkata")
    private val homeZone = ZoneId.of("Europe/Madrid")

    // 22:00 in Madrid in July: Tokyo (+7 h) and Mumbai (+3 h 30 min) are already on the next day, Los Angeles (-9 h) is not.
    private val now = Instant.parse("2026-07-15T20:00:00Z")

    private fun ready(ids: List<Long>, pendingUndo: RemovedPlace? = null, selectedId: Long? = null): WorldClockUiState.Ready {
        val byId = listOf(tokyo, losAngeles, mumbai).associateBy { it.geonamesId }
        return WorldClockUiState.Ready(
            now = now,
            homeZone = homeZone,
            homeLabel = "Madrid",
            homeCity = null,
            rows = worldClockRows(ids, byId, now, homeZone).map { WorldClockRowUi(it, zoneLabel = null) },
            land = null,
            selectedId = selectedId,
            pendingUndo = pendingUndo,
        )
    }

    private class Recorder {
        val removed = mutableListOf<Long>()
        val reordered = mutableListOf<List<Long>>()
        val added = mutableListOf<Long>()
        var undone = 0

        fun callbacks() = WorldClockCallbacks(
            onRemove = { removed += it },
            onReorder = { reordered += it },
            onAdd = { added += it },
            onUndoRemove = { undone++ },
        )
    }

    private fun show(state: WorldClockUiState, recorder: Recorder = Recorder(), picker: PickerUiState = PickerUiState()) {
        compose.setContent { UpYetTheme { WorldClockContent(state, picker, recorder.callbacks()) } }
    }

    private fun title(city: WorldCity) = string(R.string.world_clock_place_name, city.name, countryName(city.isoA2, locale))

    private fun clock(time: LocalTime) = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time)

    private fun customActionLabels(tag: String): List<String> =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    private fun invoke(tag: String, label: String) {
        val action = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
    }

    @Test fun everyPlaceRowShowsNameOffsetAndLocalTime() {
        val state = ready(listOf(1, 2, 3))
        show(state)
        val day = string(R.string.world_clock_tomorrow)
        val today = string(R.string.world_clock_today)
        val expectedOffsets = mapOf(
            1L to string(R.string.world_clock_day_offset, day, "+7 h"),
            2L to string(R.string.world_clock_day_offset, today, "−9 h"),
            3L to string(R.string.world_clock_day_offset, day, "+3 h 30 min"),
        )
        state.rows.forEach { item ->
            val row = item.row as WorldClockRow.Place
            compose.onNodeWithText(title(row.city)).assertIsDisplayed()
            compose.onNodeWithText(expectedOffsets.getValue(row.geonamesId)).assertIsDisplayed()
            val tag = "world_clock_time_${row.geonamesId}"
            compose.onNodeWithTag(tag, useUnmergedTree = true).assert(hasText(clock(row.localTime.toLocalTime())))
        }
        // The same facts reach a screen reader as one announcement per row.
        compose.onNodeWithTag("world_clock_row_1").assert(
            hasContentDescription(
                string(R.string.world_clock_row_description, title(tokyo), expectedOffsets.getValue(1L), clock(LocalTime.of(5, 0))),
            ),
        )
    }

    @Test fun homeTimeAndZoneLabelShowAboveTheList() {
        show(ready(listOf(1)))
        compose.onNodeWithText("Madrid").assertIsDisplayed()
        compose.onNodeWithText(clock(LocalTime.of(22, 0)), substring = true).assertIsDisplayed()
    }

    @Test fun unavailableRowShowsExplanationAndItsRemoveButtonRemoves() {
        val recorder = Recorder()
        show(ready(listOf(99, 1)), recorder)
        compose.onNodeWithText(string(R.string.world_clock_place_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.world_clock_place_unavailable_explanation)).assertIsDisplayed()
        compose.onNodeWithTag("world_clock_remove_99").assertIsDisplayed().performClick()
        assertThat(recorder.removed).containsExactly(99L)
    }

    @Test fun moveDownOnTheFirstRowSwapsTheFirstTwo() {
        val recorder = Recorder()
        show(ready(listOf(1, 2, 3)), recorder)
        assertThat(customActionLabels("world_clock_row_1")).doesNotContain(string(R.string.world_clock_move_up))
        invoke("world_clock_row_1", string(R.string.world_clock_move_down))
        assertThat(recorder.reordered).containsExactly(listOf(2L, 1L, 3L))
    }

    @Test fun moveUpOnTheLastRowSwapsTheLastTwo() {
        val recorder = Recorder()
        show(ready(listOf(1, 2, 3)), recorder)
        assertThat(customActionLabels("world_clock_row_3")).doesNotContain(string(R.string.world_clock_move_down))
        invoke("world_clock_row_3", string(R.string.world_clock_move_up))
        assertThat(recorder.reordered).containsExactly(listOf(1L, 3L, 2L))
    }

    @Test fun removeCustomActionRemovesThePlace() {
        val recorder = Recorder()
        show(ready(listOf(1, 2)), recorder)
        invoke("world_clock_row_2", string(R.string.world_clock_remove))
        assertThat(recorder.removed).containsExactly(2L)
    }

    @Test fun swipingStartToEndRemovesThePlace() {
        val recorder = Recorder()
        show(ready(listOf(1, 2)), recorder)
        compose.onNodeWithTag("world_clock_row_1").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertThat(recorder.removed).containsExactly(1L)
    }

    @Test fun longPressDraggingARowDownPastItsNeighbourReordersOnce() {
        val recorder = Recorder()
        show(ready(listOf(1, 2, 3)), recorder)
        val rowHeight = compose.onNodeWithTag("world_clock_row_1").fetchSemanticsNode().size.height
        compose.onNodeWithTag("world_clock_row_1").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(androidx.compose.ui.geometry.Offset(0f, rowHeight * 0.4f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, rowHeight * 0.4f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, rowHeight * 0.4f))
            up()
        }
        compose.waitForIdle()
        assertThat(recorder.reordered).containsExactly(listOf(2L, 1L, 3L))
    }

    /**
     * A press held on the handle past the long-press timeout starts the handle's detector and the row's. On an
     * emulator that threw the drag away on release (the row's detector saw the handle consume the slop event and
     * cancelled). This harness does not reproduce that timing: these two tests pass on the old code too, so they pin
     * the behaviour only. The ownership rule is pinned by ReorderStateTest; the gesture fix was verified on the
     * emulator with `adb shell input motionevent`.
     */
    @Test fun handleHeldPastLongPressThenDraggedStillReorders() {
        dragHandleOfFirstRow(holdMillis = LONG_PRESS_HOLD_MILLIS)
    }

    @Test fun handleDraggedImmediatelyReorders() {
        dragHandleOfFirstRow(holdMillis = 0)
    }

    private fun dragHandleOfFirstRow(holdMillis: Long) {
        val recorder = Recorder()
        show(ready(listOf(1, 2, 3)), recorder)
        val rowHeight = compose.onNodeWithTag("world_clock_row_1").fetchSemanticsNode().size.height
        val handle = compose.onAllNodesWithContentDescription(string(R.string.world_clock_drag_handle), useUnmergedTree = true)[0]
        handle.performTouchInput { down(center) }
        // The finger rests on the handle before moving (see the KDoc above for what this does and does not reproduce).
        compose.mainClock.advanceTimeBy(holdMillis)
        handle.performTouchInput {
            advanceEventTime(holdMillis)
            // Many small moves, as a finger produces, rather than a few jumps.
            repeat(12) { moveBy(androidx.compose.ui.geometry.Offset(0f, rowHeight * 0.1f)) }
            up()
        }
        compose.waitForIdle()
        assertThat(recorder.reordered).containsExactly(listOf(2L, 1L, 3L))
    }

    @Test fun removeAffordanceSitsLeftOfTheTimeSoASwipeNeverRevealsItUnderneath() {
        show(ready(listOf(1)))
        val label = compose.onNodeWithTag("world_clock_remove_background_1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val time = compose.onNodeWithTag("world_clock_time_1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertThat(label.right).isLessThan(time.left)
    }

    @Test fun removedPlaceOffersUndoWithItsName() {
        val recorder = Recorder()
        var state by mutableStateOf(ready(listOf(1, 2)))
        compose.setContent { UpYetTheme { WorldClockContent(state, PickerUiState(), recorder.callbacks()) } }
        // The row disappears first and the pending undo arrives with it, as the ViewModel emits them.
        state = ready(listOf(2), pendingUndo = RemovedPlace(geonamesId = 1, index = 0))
        compose.onNodeWithText(string(R.string.world_clock_removed, tokyo.name)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.world_clock_undo)).performClick()
        compose.waitForIdle()
        assertThat(recorder.undone).isEqualTo(1)
    }

    @Test fun emptyListShowsTheHintAndStillShowsTheHomeTime() {
        show(ready(emptyList()))
        compose.onNodeWithText(string(R.string.world_clock_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.world_clock_empty_hint)).assertIsDisplayed()
        compose.onNodeWithTag("world_clock_home").assertIsDisplayed()
        compose.onNodeWithTag("world_clock_map").assertIsDisplayed()
    }

    @Test fun loadingShowsAProgressIndicator() {
        show(WorldClockUiState.Loading)
        compose.onNodeWithTag("world_clock_loading").assertIsDisplayed()
    }

    @Test fun addButtonOpensThePickerAndPickingACityAddsItAndCloses() {
        val recorder = Recorder()
        val picker = PickerUiState(query = "tok", results = listOf(tokyo), addedIds = emptySet())
        show(ready(emptyList()), recorder, picker)
        compose.onNodeWithTag("world_clock_add").performClick()
        compose.onNodeWithTag("world_clock_result_1").performClick()
        compose.waitForIdle()
        assertThat(recorder.added).containsExactly(1L)
        compose.onNodeWithTag("world_clock_picker").assertDoesNotExist()
    }

    private fun showPicker(picker: PickerUiState, recorder: Recorder = Recorder()) {
        compose.setContent {
            UpYetTheme {
                CityPicker(picker, now, homeZone, onQueryChange = {}, onAdd = { recorder.added += it }, onClose = {})
            }
        }
    }

    @Test fun pickerResultShowsCityCountryTimeAndOffset() {
        showPicker(PickerUiState(query = "tok", results = listOf(tokyo)))
        compose.onNodeWithText(title(tokyo)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.world_clock_day_offset, string(R.string.world_clock_tomorrow), "+7 h")).assertIsDisplayed()
        compose.onNodeWithText(clock(LocalTime.of(5, 0))).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.world_clock_attribution)).assertIsDisplayed()
    }

    @Test fun pickerAddedCityIsMarkedAndCannotBeAddedAgain() {
        val recorder = Recorder()
        showPicker(PickerUiState(query = "o", results = listOf(tokyo, losAngeles), addedIds = setOf(1L)), recorder)
        compose.onNodeWithTag(
            "world_clock_result_1",
        ).assertHasNoClickAction().assert(hasContentDescription(string(R.string.world_clock_added)))
        compose.onNodeWithTag("world_clock_result_2").performClick()
        assertThat(recorder.added).containsExactly(2L)
    }

    @Test fun pickerBlankQueryShowsTheHintAndKeepsTheAttribution() {
        showPicker(PickerUiState())
        compose.onNodeWithText(string(R.string.world_clock_picker_empty_query)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.world_clock_attribution)).assertIsDisplayed()
    }

    @Test fun pickerWithNoMatchesSaysSo() {
        showPicker(PickerUiState(query = "zzzz"))
        compose.onNodeWithText(string(R.string.world_clock_picker_no_results)).assertIsDisplayed()
    }
}

/** Comfortably past the platform long-press timeout (400 ms by default). */
private const val LONG_PRESS_HOLD_MILLIS = 800L
