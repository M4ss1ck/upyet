package dev.upyet.settings.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import dev.upyet.testing.FakeDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WorldClockRepositoryTest {
    private val key = stringPreferencesKey("world_clock_city_ids")

    @Test
    fun emptyByDefault() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))

        assertThat(repository.cityIds.first()).isEmpty()
    }

    @Test
    fun addAppendsInOrder() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))

        repository.add(12)
        repository.add(7)
        repository.add(30)

        assertThat(repository.cityIds.first()).containsExactly(12L, 7L, 30L).inOrder()
    }

    @Test
    fun addDuplicateIsNoOp() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))
        repository.add(12)
        repository.add(7)

        repository.add(12)

        assertThat(repository.cityIds.first()).containsExactly(12L, 7L).inOrder()
    }

    @Test
    fun removeDropsId() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))
        repository.add(12)
        repository.add(7)
        repository.add(30)

        repository.remove(7)

        assertThat(repository.cityIds.first()).containsExactly(12L, 30L).inOrder()
    }

    @Test
    fun removeAbsentIsNoOp() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))
        repository.add(12)

        repository.remove(99)

        assertThat(repository.cityIds.first()).containsExactly(12L)
    }

    @Test
    fun removeLastLeavesEmpty() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))
        repository.add(12)

        repository.remove(12)

        assertThat(repository.cityIds.first()).isEmpty()
    }

    @Test
    fun insertAtIndex() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))
        repository.add(12)
        repository.add(30)

        repository.insert(7, 1)

        assertThat(repository.cityIds.first()).containsExactly(12L, 7L, 30L).inOrder()
    }

    @Test
    fun insertBeyondSizeAppends() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))
        repository.add(12)

        repository.insert(7, 50)

        assertThat(repository.cityIds.first()).containsExactly(12L, 7L).inOrder()
    }

    @Test
    fun insertNegativeIndexPrepends() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))
        repository.add(12)

        repository.insert(7, -3)

        assertThat(repository.cityIds.first()).containsExactly(7L, 12L).inOrder()
    }

    @Test
    fun insertExistingIsNoOp() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))
        repository.add(12)
        repository.add(7)

        repository.insert(12, 1)

        assertThat(repository.cityIds.first()).containsExactly(12L, 7L).inOrder()
    }

    @Test
    fun setOrderReorders() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))
        repository.add(12)
        repository.add(7)
        repository.add(30)

        repository.setOrder(listOf(30L, 12L, 7L))

        assertThat(repository.cityIds.first()).containsExactly(30L, 12L, 7L).inOrder()
    }

    @Test
    fun setOrderDropsUnknownIds() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))
        repository.add(12)
        repository.add(7)

        repository.setOrder(listOf(7L, 999L, 12L))

        assertThat(repository.cityIds.first()).containsExactly(7L, 12L).inOrder()
    }

    @Test
    fun setOrderKeepsStoredIdsMissingFromArgumentAtEnd() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))
        repository.add(12)
        repository.add(7)
        repository.add(30)

        repository.setOrder(listOf(30L))

        assertThat(repository.cityIds.first()).containsExactly(30L, 12L, 7L).inOrder()
    }

    @Test
    fun setOrderDeduplicatesArgument() = runTest {
        val repository = WorldClockRepository(FakeDataStore(emptyPreferences()))
        repository.add(12)
        repository.add(7)

        repository.setOrder(listOf(7L, 7L, 12L))

        assertThat(repository.cityIds.first()).containsExactly(7L, 12L).inOrder()
    }

    @Test
    fun malformedStoredValueDecodesTolerantly() = runTest {
        val repository =
            WorldClockRepository(FakeDataStore(preferencesOf(key to "12,,abc,12,7")))

        assertThat(repository.cityIds.first()).containsExactly(12L, 7L).inOrder()
    }

    @Test
    fun emptyStoredStringDecodesToEmpty() = runTest {
        val repository = WorldClockRepository(FakeDataStore(preferencesOf(key to "")))

        assertThat(repository.cityIds.first()).isEmpty()
    }
}
