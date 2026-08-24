package dev.myalarm.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceSegmentDao {
    @Query(
        "SELECT * FROM evidence_segments WHERE occurrenceId = :occurrenceId ORDER BY requestedAt",
    )
    fun observeForOccurrence(occurrenceId: Long): Flow<List<EvidenceSegmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(segment: EvidenceSegmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(segment: EvidenceSegmentEntity)

    // Retention is based on when the segment was requested: failed segments never get a finalizedAt,
    // and in-progress segments (REQUESTED/RECORDING) must never be selected for deletion.
    @Query(
        """
        SELECT * FROM evidence_segments
        WHERE requestedAt < :cutoffMillis AND status NOT IN ('REQUESTED', 'RECORDING')
        """,
    )
    suspend fun selectFinishedBefore(cutoffMillis: Long): List<EvidenceSegmentEntity>

    @Query(
        "DELETE FROM evidence_segments WHERE id IN (:ids)",
    )
    suspend fun deleteByIds(ids: List<Long>)
}
