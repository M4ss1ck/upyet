package dev.upyet.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "evidence_segments",
    foreignKeys = [
        ForeignKey(
            entity = AlarmOccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("occurrenceId")],
)
data class EvidenceSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurrenceId: Long,
    val requestedAt: Long,
    val startedAt: Long?,
    val endedAt: Long?,
    val finalizedAt: Long?,
    val fileName: String?,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val status: String,
    val errorCode: String?,
)
