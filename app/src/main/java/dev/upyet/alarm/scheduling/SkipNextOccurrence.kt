package dev.upyet.alarm.scheduling

import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.SkipNext
import dev.upyet.core.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkipNextOccurrence @Inject constructor(
    private val repository: AlarmRepository,
    private val timeProvider: TimeProvider,
    private val rescheduler: AlarmRescheduler,
) {
    suspend fun skip(alarm: Alarm): SchedulingResult? {
        if (!SkipNext.canSkip(alarm)) return null
        val zone = timeProvider.zone()
        val now = timeProvider.now()
        if (SkipNext.activeDate(alarm, zone, now) != null) return SchedulingResult.Scheduled
        val skipOn = SkipNext.target(alarm, zone, now) ?: return null
        repository.setSkipNextOn(alarm.id, skipOn)
        return rescheduler.scheduleNext(alarm.copy(skipNextOn = skipOn))
    }

    suspend fun unskip(alarm: Alarm): SchedulingResult? {
        if (!SkipNext.canSkip(alarm)) return null
        if (SkipNext.activeDate(alarm, timeProvider.zone(), timeProvider.now()) == null) return SchedulingResult.Scheduled
        repository.setSkipNextOn(alarm.id, null)
        return rescheduler.scheduleNext(alarm.copy(skipNextOn = null))
    }
}
