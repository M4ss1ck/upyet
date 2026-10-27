package dev.upyet.worldclock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.core.di.DefaultDispatcher
import dev.upyet.core.time.TimeProvider
import dev.upyet.settings.data.WorldClockRepository
import dev.upyet.worldclock.data.WorldCity
import dev.upyet.worldclock.data.WorldClockAssets
import dev.upyet.worldclock.data.WorldLand
import dev.upyet.worldclock.domain.CitySearchIndex
import dev.upyet.worldclock.domain.WorldClockRow
import dev.upyet.worldclock.domain.homeCity
import dev.upyet.worldclock.domain.worldClockRows
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.IllformedLocaleException
import java.util.Locale
import javax.inject.Inject

/** Keeps the flows alive for a while after the last collector leaves, so a quick tab switch does not restart them. */
private const val STOP_TIMEOUT_MS = 5_000L
private const val SECONDS_PER_MINUTE = 60L
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val NANOS_PER_MINUTE = SECONDS_PER_MINUTE * NANOS_PER_SECOND
private const val NANOS_PER_MILLI = 1_000_000L

/**
 * The World clock tab's state (ADR 0011).
 *
 * The minute ticker below is a display refresh only: it re-reads the clock so the list shows the current minute.
 * AGENTS.md §6 forbids `delay` for scheduling alarms, not for redrawing a label, and nothing here rings or records.
 */
@HiltViewModel
class WorldClockViewModel @Inject constructor(
    private val assets: WorldClockAssets,
    private val repository: WorldClockRepository,
    private val time: TimeProvider,
    private val zoneLabeler: ZoneLabeler,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private class Catalog(
        val cities: List<WorldCity>,
        val land: WorldLand?,
        val citiesById: Map<Long, WorldCity>,
        val index: CitySearchIndex,
    )

    private class Clock(val now: Instant, val zone: ZoneId)

    /** Loaded once per process on first use, then shared by the state and the picker. */
    private val catalog: Deferred<Catalog> =
        viewModelScope.async(dispatcher, start = CoroutineStart.LAZY) { loadCatalog() }

    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val selectedId = MutableStateFlow<Long?>(null)
    private val pendingUndo = MutableStateFlow<RemovedPlace?>(null)
    private val searchQuery = MutableStateFlow("")

    val state: StateFlow<WorldClockUiState> = flow {
        emitAll(readyStates(catalog.await()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WorldClockUiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val picker: StateFlow<PickerUiState> = combine(
        searchQuery,
        searchQuery.mapLatest { query -> if (query.isBlank()) emptyList() else catalog.await().index.search(query) },
        repository.cityIds,
    ) { query, results, ids -> PickerUiState(query = query, results = results, addedIds = ids.toSet()) }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PickerUiState())

    fun onSelect(id: Long) {
        selectedId.update { if (it == id) null else id }
    }

    fun onAdd(id: Long) {
        viewModelScope.launch { repository.add(id) }
    }

    /** Index is read from the stored order, so undo can put the place back where it was. A place not stored is a no-op. */
    fun onRemove(id: Long) {
        viewModelScope.launch {
            val index = repository.cityIds.first().indexOf(id)
            if (index < 0) return@launch
            repository.remove(id)
            pendingUndo.value = RemovedPlace(geonamesId = id, index = index)
            selectedId.update { if (it == id) null else it }
        }
    }

    fun onUndoRemove() {
        val removed = pendingUndo.value ?: return
        pendingUndo.value = null
        viewModelScope.launch { repository.insert(removed.geonamesId, removed.index) }
    }

    fun onUndoDismissed() {
        pendingUndo.value = null
    }

    fun onReorder(ids: List<Long>) {
        viewModelScope.launch { repository.setOrder(ids) }
    }

    fun onQueryChange(query: String) {
        searchQuery.value = query
    }

    /** Re-reads the clock and the zone now, for a screen resume or a zone change. */
    fun onRefresh() {
        refreshes.tryEmit(Unit)
    }

    private suspend fun loadCatalog(): Catalog {
        val cities = assets.cities()
        val land = assets.land()
        return Catalog(
            cities = cities,
            land = land,
            citiesById = cities.associateBy { it.geonamesId },
            index = CitySearchIndex(cities, ::countryNames),
        )
    }

    /** The country names a query may match, in the device language, English and Spanish (the gazetteer's two). */
    private fun countryNames(isoA2: String): Set<String> = listOf(Locale.getDefault(), Locale.ENGLISH, Locale.forLanguageTag("es"))
        .mapTo(mutableSetOf()) { countryName(isoA2, it) }

    private fun countryName(isoA2: String, locale: Locale): String = try {
        Locale.Builder().setRegion(isoA2).build().getDisplayCountry(locale)
    } catch (e: IllformedLocaleException) {
        isoA2
    }

    private fun readyStates(catalog: Catalog): Flow<WorldClockUiState> {
        // Labels are per zone and never change within a process, so each zone is asked once per subscription.
        val labels = HashMap<ZoneId, String?>()
        fun label(zone: ZoneId): String? =
            if (labels.containsKey(zone)) labels[zone] else zoneLabeler.label(zone, Locale.getDefault()).also { labels[zone] = it }

        return combine(minuteTicks(), repository.cityIds, selectedId, pendingUndo) { clock, ids, selected, undo ->
            val rows = worldClockRows(ids, catalog.citiesById, clock.now, clock.zone).map { row ->
                val zoneLabel = when (row) {
                    is WorldClockRow.Place -> label(row.city.zoneId)
                    is WorldClockRow.Unavailable -> null
                }
                WorldClockRowUi(row, zoneLabel)
            }
            WorldClockUiState.Ready(
                now = clock.now,
                homeZone = clock.zone,
                homeLabel = label(clock.zone),
                homeCity = homeCity(catalog.cities, clock.zone),
                rows = rows,
                land = catalog.land,
                selectedId = selected,
                pendingUndo = undo,
            )
        }.flowOn(dispatcher)
    }

    /**
     * Emits the clock now, then again at each minute boundary. [onRefresh] restarts it at once. Only the display
     * depends on this; nothing is scheduled from it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun minuteTicks(): Flow<Clock> = refreshes
        .onStart { emit(Unit) }
        .flatMapLatest {
            flow {
                while (true) {
                    val now = time.now()
                    emit(Clock(now, time.zone()))
                    delay(millisToNextMinute(now))
                }
            }
        }
}

/**
 * Milliseconds from [now] to the next whole minute, in 1..60_000. Rounded up, so the wake-up lands at or after the
 * boundary and never shows the old minute again. Exactly on a boundary is a full minute away.
 */
internal fun millisToNextMinute(now: Instant): Long {
    val nanosIntoMinute = Math.floorMod(now.epochSecond, SECONDS_PER_MINUTE) * NANOS_PER_SECOND + now.nano
    val remainingNanos = NANOS_PER_MINUTE - nanosIntoMinute
    return (remainingNanos + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI
}
