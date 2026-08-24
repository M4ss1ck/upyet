package dev.myalarm.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class OccurrenceWithSegments(
    @androidx.room.Embedded val occurrence: AlarmOccurrenceEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "occurrenceId",
    )
    val segments: List<EvidenceSegmentEntity>,
)

@Dao
interface OccurrenceDao {
    @Query(
        "SELECT * FROM alarm_occurrences ORDER BY scheduledFor DESC",
    )
    fun observeAll(): Flow<List<AlarmOccurrenceEntity>>

    @Query("SELECT * FROM alarm_occurrences WHERE id = :id")
    fun observeById(id: Long): Flow<AlarmOccurrenceEntity?>

    @Transaction
    @Query("SELECT * FROM alarm_occurrences WHERE id = :id")
    fun observeWithSegments(id: Long): Flow<OccurrenceWithSegments?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(occurrence: AlarmOccurrenceEntity): Long

    @Query(
        "UPDATE alarm_occurrences SET activityVisibleAt = :at WHERE id = :id",
    )
    suspend fun markActivityVisible(id: Long, at: Long)

    @Query(
        "UPDATE alarm_occurrences SET dismissedAt = :dismissedAt, outcome = :outcome WHERE id = :id",
    )
    suspend fun complete(id: Long, dismissedAt: Long?, outcome: String)

    @Query("DELETE FROM alarm_occurrences WHERE id = :id")
    suspend fun deleteById(id: Long)
}
