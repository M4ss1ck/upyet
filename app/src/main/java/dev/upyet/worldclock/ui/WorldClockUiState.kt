package dev.upyet.worldclock.ui

import dev.upyet.worldclock.data.WorldCity
import dev.upyet.worldclock.data.WorldLand
import dev.upyet.worldclock.domain.WorldClockRow
import java.time.Instant
import java.time.ZoneId

/** The World clock tab. [Loading] until the gazetteer is read; the list is then always [Ready], even when empty. */
sealed interface WorldClockUiState {
    data object Loading : WorldClockUiState

    data class Ready(
        val now: Instant,
        val homeZone: ZoneId,
        /** ICU exemplar of the home zone. */
        val homeLabel: String?,
        /** The map's home marker: the most populous place in the home zone. */
        val homeCity: WorldCity?,
        val rows: List<WorldClockRowUi>,
        val land: WorldLand?,
        /** The highlighted row and map marker. */
        val selectedId: Long?,
        /** The last removal, offered for undo until dismissed. */
        val pendingUndo: RemovedPlace?,
    ) : WorldClockUiState
}

/** One list line. [zoneLabel] is null for [WorldClockRow.Unavailable]. */
data class WorldClockRowUi(val row: WorldClockRow, val zoneLabel: String?)

/** A removed place and the position it held, so undo can put it back. */
data class RemovedPlace(val geonamesId: Long, val index: Int)

data class PickerUiState(val query: String = "", val results: List<WorldCity> = emptyList(), val addedIds: Set<Long> = emptySet())
