package dev.upyet.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The world clock's chosen places, as one ordered list of gazetteer `geonames_id`s (ADR 0011).
 *
 * Only ids are stored, never display strings: names are localized at read time from the gazetteer,
 * so a language change never rewrites settings. A stored id may stop resolving (the gazetteer
 * shipped without it); it is kept here and never pruned, so a later gazetteer still shows it.
 *
 * Every mutation is one `edit {}` so its read-modify-write is atomic with respect to other edits.
 */
@Singleton
class WorldClockRepository
@Inject
constructor(private val dataStore: DataStore<Preferences>) {
    val cityIds: Flow<List<Long>> =
        dataStore.data.map { preferences -> decode(preferences[Keys.cityIds]) }

    suspend fun add(id: Long) {
        dataStore.edit { preferences ->
            val current = decode(preferences[Keys.cityIds])
            if (id !in current) preferences.write(current + id)
        }
    }

    /**
     * Returns the position [id] held, read in the same edit that removes it so an undo cannot restore it to a stale
     * slot; null when it was not stored.
     */
    suspend fun remove(id: Long): Int? {
        var removedAt: Int? = null
        dataStore.edit { preferences ->
            val ids = decode(preferences[Keys.cityIds])
            removedAt = ids.indexOf(id).takeIf { it >= 0 }
            preferences.write(ids - id)
        }
        return removedAt
    }

    /** Undo-remove re-inserts at the remembered position; an out-of-range index clamps to the ends. */
    suspend fun insert(id: Long, index: Int) {
        dataStore.edit { preferences ->
            val current = decode(preferences[Keys.cityIds])
            if (id in current) return@edit
            val at = index.coerceIn(0, current.size)
            preferences.write(current.toMutableList().apply { add(at, id) })
        }
    }

    /**
     * Reorders the stored ids. Ids in [ids] that are not stored are dropped, so a reorder cannot
     * introduce an id. Stored ids missing from [ids] stay, at the end, so a concurrent [add] that
     * this caller has not seen is never lost.
     */
    suspend fun setOrder(ids: List<Long>) {
        dataStore.edit { preferences ->
            val stored = decode(preferences[Keys.cityIds])
            val requested = ids.distinct().filter { it in stored }
            preferences.write(requested + stored.filter { it !in ids })
        }
    }

    private fun MutablePreferences.write(ids: List<Long>) {
        if (ids.isEmpty()) remove(Keys.cityIds) else set(Keys.cityIds, ids.joinToString(SEPARATOR))
    }

    /** Tolerant: a hand-edited or corrupt value keeps its valid ids in first-seen order. */
    private fun decode(raw: String?): List<Long> = raw.orEmpty().split(SEPARATOR).mapNotNull { it.trim().toLongOrNull() }.distinct()

    private object Keys {
        val cityIds = stringPreferencesKey("world_clock_city_ids")
    }

    private companion object {
        const val SEPARATOR = ","
    }
}
