package dev.upyet.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerDao {
    /** Creation order: the list shows timers in the order they were added. */
    @Query("SELECT * FROM timers ORDER BY createdAt, id")
    fun observeAll(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun findById(id: Long): TimerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(timer: TimerEntity): Long

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
