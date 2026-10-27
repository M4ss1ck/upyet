package dev.upyet.worldclock.ui

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import dev.upyet.core.time.TimeProvider
import dev.upyet.settings.data.WorldClockRepository
import dev.upyet.testing.FakeDataStore
import dev.upyet.worldclock.data.WorldCity
import dev.upyet.worldclock.data.WorldClockAssets
import dev.upyet.worldclock.data.WorldLand
import dev.upyet.worldclock.domain.WorldClockRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class WorldClockViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun startsLoadingThenReady() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(1))
        h.watch(this)

        assertThat(h.vm.state.value).isEqualTo(WorldClockUiState.Loading)

        runCurrent()

        assertThat(h.vm.state.value).isInstanceOf(WorldClockUiState.Ready::class.java)
    }

    @Test fun rowsFollowStoredOrderWithLocalTimeAndOffset() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(2, 3, 1))
        h.watch(this)
        runCurrent()

        val rows = ready(h).rows.map { it.row }
        assertThat(rows.map { it.geonamesId }).containsExactly(2L, 3L, 1L).inOrder()
        val losAngeles = rows[0] as WorldClockRow.Place
        assertThat(losAngeles.localTime).isEqualTo(LocalDateTime.of(2026, 10, 27, 5, 0))
        assertThat(losAngeles.offsetMinutes).isEqualTo(-480)
        assertThat(losAngeles.dayDelta).isEqualTo(0)
        val tokyo = rows[1] as WorldClockRow.Place
        assertThat(tokyo.localTime).isEqualTo(LocalDateTime.of(2026, 10, 27, 21, 0))
        assertThat(tokyo.dayDelta).isEqualTo(0)
    }

    @Test fun zoneLabelsAndHomeLabelComeFromTheLabeler() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(2, 99))
        h.watch(this)
        runCurrent()

        val ready = ready(h)
        assertThat(ready.homeLabel).isEqualTo("Madrid")
        assertThat(ready.rows[0].zoneLabel).isEqualTo("Los Angeles")
        assertThat(ready.rows[1].row).isEqualTo(WorldClockRow.Unavailable(99L))
        assertThat(ready.rows[1].zoneLabel).isNull()
    }

    @Test fun unknownStoredIdIsUnavailableInPlaceAndNeverPruned() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(2, 99, 1))
        h.watch(this)
        runCurrent()

        val rows = ready(h).rows.map { it.row }
        assertThat(rows[1]).isEqualTo(WorldClockRow.Unavailable(99L))
        assertThat(h.repository.cityIds.first()).containsExactly(2L, 99L, 1L).inOrder()
    }

    @Test fun addAppendsToTheStoredList() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(1))
        h.watch(this)
        runCurrent()

        h.vm.onAdd(3)
        runCurrent()

        assertThat(h.repository.cityIds.first()).containsExactly(1L, 3L).inOrder()
        assertThat(ready(h).rows).hasSize(2)
    }

    @Test fun removeRecordsUndoAtItsIndexAndUndoReinsertsThere() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(1, 2, 3))
        h.watch(this)
        runCurrent()

        h.vm.onRemove(2)
        runCurrent()

        assertThat(h.repository.cityIds.first()).containsExactly(1L, 3L).inOrder()
        assertThat(ready(h).pendingUndo).isEqualTo(RemovedPlace(geonamesId = 2L, index = 1))

        h.vm.onUndoRemove()
        runCurrent()

        assertThat(h.repository.cityIds.first()).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(ready(h).pendingUndo).isNull()
    }

    @Test fun undoDismissedClearsThePendingUndo() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(1, 2))
        h.watch(this)
        runCurrent()

        h.vm.onRemove(1)
        runCurrent()
        h.vm.onUndoDismissed()
        runCurrent()
        h.vm.onUndoRemove()
        runCurrent()

        assertThat(ready(h).pendingUndo).isNull()
        assertThat(h.repository.cityIds.first()).containsExactly(2L)
    }

    @Test fun reorderPersistsThroughTheRepository() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(1, 2, 3))
        h.watch(this)
        runCurrent()

        h.vm.onReorder(listOf(3, 1, 2))
        runCurrent()

        assertThat(h.repository.cityIds.first()).containsExactly(3L, 1L, 2L).inOrder()
    }

    @Test fun selectToggles() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(1, 2))
        h.watch(this)
        runCurrent()

        h.vm.onSelect(2)
        runCurrent()
        assertThat(ready(h).selectedId).isEqualTo(2L)

        h.vm.onSelect(2)
        runCurrent()
        assertThat(ready(h).selectedId).isNull()
    }

    @Test fun removingTheSelectedPlaceClearsTheSelection() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(1, 2))
        h.watch(this)
        runCurrent()

        h.vm.onSelect(1)
        h.vm.onRemove(1)
        runCurrent()

        assertThat(ready(h).selectedId).isNull()
    }

    @Test fun removingAnotherPlaceKeepsTheSelection() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(1, 2))
        h.watch(this)
        runCurrent()

        h.vm.onSelect(1)
        h.vm.onRemove(2)
        runCurrent()

        assertThat(ready(h).selectedId).isEqualTo(1L)
    }

    @Test fun minuteTickerReemitsAtTheNextMinuteBoundary() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(2), start = Instant.parse("2026-10-27T12:00:30Z"))
        h.watch(this)
        runCurrent()
        assertThat(ready(h).now).isEqualTo(Instant.parse("2026-10-27T12:00:30Z"))

        h.time.instant = Instant.parse("2026-10-27T12:01:00Z")
        advanceTimeBy(29_999)
        runCurrent()
        assertThat(ready(h).now).isEqualTo(Instant.parse("2026-10-27T12:00:30Z"))

        advanceTimeBy(1)
        runCurrent()
        assertThat(ready(h).now).isEqualTo(Instant.parse("2026-10-27T12:01:00Z"))
    }

    @Test fun onRefreshPicksUpAChangedZoneImmediately() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(1))
        h.watch(this)
        runCurrent()
        assertThat(ready(h).homeZone).isEqualTo(MADRID)

        h.time.zoneId = TOKYO
        h.vm.onRefresh()
        runCurrent()

        val ready = ready(h)
        assertThat(ready.homeZone).isEqualTo(TOKYO)
        assertThat(ready.homeLabel).isEqualTo("Tokyo")
        assertThat(ready.homeCity?.geonamesId).isEqualTo(3L)
    }

    @Test fun pickerSearchesByQueryAndBlankQueryIsEmpty() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(1))
        h.watch(this)
        runCurrent()

        h.vm.onQueryChange("los")
        runCurrent()
        assertThat(h.vm.picker.value.query).isEqualTo("los")
        assertThat(h.vm.picker.value.results.map { it.geonamesId }).containsExactly(2L)

        h.vm.onQueryChange("   ")
        runCurrent()
        assertThat(h.vm.picker.value.results).isEmpty()
    }

    @Test fun pickerAddedIdsReflectTheStoredIds() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(1, 3))
        h.watch(this)
        runCurrent()

        assertThat(h.vm.picker.value.addedIds).containsExactly(1L, 3L)

        h.vm.onAdd(2)
        runCurrent()

        assertThat(h.vm.picker.value.addedIds).containsExactly(1L, 2L, 3L)
    }

    @Test fun emptyGazetteerIsReadyWithEveryRowUnavailable() = runTest(dispatcher) {
        val h = Harness(storedIds = listOf(1, 2), cities = emptyList())
        h.watch(this)
        runCurrent()

        val ready = ready(h)
        assertThat(ready.rows.map { it.row }).containsExactly(
            WorldClockRow.Unavailable(1L),
            WorldClockRow.Unavailable(2L),
        ).inOrder()
        assertThat(ready.homeCity).isNull()
        assertThat(ready.land).isNull()
    }

    @Test fun millisToNextMinuteIsOneToSixtyThousand() {
        assertThat(millisToNextMinute(Instant.parse("2026-10-27T12:00:30.250Z"))).isEqualTo(29_750L)
        assertThat(millisToNextMinute(Instant.parse("2026-10-27T12:01:00Z"))).isEqualTo(60_000L)
        assertThat(millisToNextMinute(Instant.parse("2026-10-27T12:00:59.999Z"))).isEqualTo(1L)
        assertThat(millisToNextMinute(Instant.parse("2026-10-27T12:00:59.999999999Z"))).isEqualTo(1L)
        assertThat(millisToNextMinute(Instant.parse("2026-10-27T12:00:00.001Z"))).isEqualTo(59_999L)
    }

    private fun ready(h: Harness): WorldClockUiState.Ready = h.vm.state.value as WorldClockUiState.Ready

    private inner class Harness(storedIds: List<Long>, cities: List<WorldCity> = CITIES, start: Instant = START) {
        val time = MutableTimeProvider(start, MADRID)
        val repository = WorldClockRepository(
            FakeDataStore(mutablePreferencesOf(IDS_KEY to storedIds.joinToString(","))),
        )
        val vm = WorldClockViewModel(
            assets = FakeWorldClockAssets(cities, land = null),
            repository = repository,
            time = time,
            zoneLabeler = MapZoneLabeler(LABELS),
            dispatcher = dispatcher,
        )

        fun watch(scope: TestScope) {
            scope.backgroundScope.launch { vm.state.collect {} }
            scope.backgroundScope.launch { vm.picker.collect {} }
        }
    }

    private class FakeWorldClockAssets(private val citiesList: List<WorldCity>, private val land: WorldLand?) : WorldClockAssets {
        override suspend fun cities(): List<WorldCity> = citiesList

        override suspend fun land(): WorldLand? = land
    }

    private class MutableTimeProvider(var instant: Instant, var zoneId: ZoneId) : TimeProvider {
        override fun now(): Instant = instant

        override fun zone(): ZoneId = zoneId
    }

    private class MapZoneLabeler(private val labels: Map<ZoneId, String>) : ZoneLabeler {
        override fun label(zone: ZoneId, locale: Locale): String? = labels[zone]
    }

    private companion object {
        val IDS_KEY = stringPreferencesKey("world_clock_city_ids")
        val START: Instant = Instant.parse("2026-10-27T12:00:00Z")
        val MADRID: ZoneId = ZoneId.of("Europe/Madrid")
        val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")

        val LABELS: Map<ZoneId, String> = mapOf(
            MADRID to "Madrid",
            ZoneId.of("America/Los_Angeles") to "Los Angeles",
            TOKYO to "Tokyo",
        )

        val CITIES: List<WorldCity> = listOf(
            city(1L, "Madrid", "ES", "Europe/Madrid", 3_200_000),
            city(2L, "Los Angeles", "US", "America/Los_Angeles", 3_900_000),
            city(3L, "Tokyo", "JP", "Asia/Tokyo", 13_900_000),
        )

        fun city(id: Long, name: String, iso: String, zone: String, population: Long) = WorldCity(
            name = name,
            nameEs = name,
            isoA2 = iso,
            zoneId = ZoneId.of(zone),
            latitude = 0.0,
            longitude = 0.0,
            population = population,
            geonamesId = id,
        )
    }
}
