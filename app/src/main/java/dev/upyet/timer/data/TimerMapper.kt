package dev.upyet.timer.data

import dev.upyet.core.database.TimerEntity
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import java.time.Duration
import java.time.Instant

private const val IDLE = "IDLE"
private const val RUNNING = "RUNNING"
private const val PAUSED = "PAUSED"
private const val RINGING = "RINGING"
private const val MISSED = "MISSED"

fun TimerEntity.toDomain(): Timer = Timer.restore(
    id = TimerId(id),
    length = Duration.ofMillis(lengthMillis),
    label = label,
    ringtoneUri = ringtoneUri,
    evidenceEnabled = evidenceEnabled,
    runState = decodeRunState(),
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun Timer.toEntity(): TimerEntity {
    val running = runState as? TimerRunState.Running
    return TimerEntity(
        id = id.value,
        lengthMillis = length.toMillis(),
        label = label,
        ringtoneUri = ringtoneUri,
        evidenceEnabled = evidenceEnabled,
        runState = when (runState) {
            TimerRunState.Idle -> IDLE
            is TimerRunState.Running -> RUNNING
            is TimerRunState.Paused -> PAUSED
            TimerRunState.Ringing -> RINGING
            TimerRunState.Missed -> MISSED
        },
        remainingAtAnchorMillis = when (runState) {
            is TimerRunState.Running -> runState.remainingAtAnchor.toMillis()
            is TimerRunState.Paused -> runState.remaining.toMillis()
            TimerRunState.Idle, TimerRunState.Ringing, TimerRunState.Missed -> null
        },
        anchorElapsedRealtimeMillis = running?.anchor?.elapsedRealtime?.toMillis(),
        anchorWallClockMillis = running?.anchor?.wallClock?.toEpochMilli(),
        createdAt = createdAt.toEpochMilli(),
    )
}

private fun TimerEntity.decodeRunState(): TimerRunState = when (runState) {
    IDLE -> TimerRunState.Idle

    RUNNING -> TimerRunState.Running(
        remainingAtAnchor = Duration.ofMillis(checkNotNull(remainingAtAnchorMillis) { "running timer $id has no remaining" }),
        anchor = TimerAnchor(
            elapsedRealtime = Duration.ofMillis(checkNotNull(anchorElapsedRealtimeMillis) { "running timer $id has no elapsed anchor" }),
            wallClock = Instant.ofEpochMilli(checkNotNull(anchorWallClockMillis) { "running timer $id has no wall-clock anchor" }),
        ),
    )

    PAUSED -> TimerRunState.Paused(Duration.ofMillis(checkNotNull(remainingAtAnchorMillis) { "paused timer $id has no remaining" }))

    RINGING -> TimerRunState.Ringing

    MISSED -> TimerRunState.Missed

    else -> error("Unknown timer run state: $runState")
}
