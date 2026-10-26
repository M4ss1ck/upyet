package dev.upyet.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One timer in the list (ADR 0008). Room is the truth for the list, labels and editing; the device-protected
 * mirror only carries what rings before first unlock. Metadata only (AGENTS.md §12).
 */
@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lengthMillis: Long,
    val label: String,
    val ringtoneUri: String?,
    val evidenceEnabled: Boolean,
    /** IDLE, RUNNING, PAUSED, RINGING or MISSED. */
    val runState: String,
    /** RUNNING: remaining at the anchor. PAUSED: the frozen remaining time. Otherwise null. */
    val remainingAtAnchorMillis: Long?,
    /** RUNNING only: `SystemClock.elapsedRealtime()` at the anchor. */
    val anchorElapsedRealtimeMillis: Long?,
    /** RUNNING only: the wall clock at the anchor. */
    val anchorWallClockMillis: Long?,
    val createdAt: Long,
)
