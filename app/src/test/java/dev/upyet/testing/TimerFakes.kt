package dev.upyet.testing

import dev.upyet.core.directboot.MirroredTimer
import dev.upyet.core.directboot.TimerMirror
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRepository
import dev.upyet.timer.scheduling.TimerAlarm
import dev.upyet.timer.scheduling.TimerArmResult
import dev.upyet.timer.scheduling.TimerMissedNotices
import dev.upyet.timer.scheduling.TimerScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory Room stand-in: ids are assigned like `autoGenerate`, and the list keeps creation order. */
class FakeTimerRepository(initial: List<Timer> = emptyList()) : TimerRepository {
    private val timers = MutableStateFlow(initial.associateBy { it.id })
    var upserts = 0
        private set

    val all: List<Timer> get() = timers.value.values.toList()

    override fun observeTimers(): Flow<List<Timer>> = timers.map { it.values.toList() }

    override suspend fun getTimer(id: TimerId): Timer? = timers.value[id]

    override suspend fun upsert(timer: Timer): TimerId {
        upserts++
        timers.value = timers.value + (timer.id to timer)
        return timer.id
    }

    override suspend fun delete(id: TimerId) {
        timers.value = timers.value - id
    }
}

/** Behaves like `DirectBootTimerStore`: the store, not the caller, marks a record written while locked. */
class FakeTimerMirror(var unlocked: Boolean = true) : TimerMirror {
    private val records = linkedMapOf<TimerId, MirroredTimer>()

    override fun put(record: MirroredTimer) {
        val pending = !unlocked || records[record.timerId]?.writtenWhileLocked == true
        records[record.timerId] = record.copy(writtenWhileLocked = pending)
    }

    override fun markReconciled(timerId: TimerId) {
        records[timerId]?.let { records[timerId] = it.copy(writtenWhileLocked = false) }
    }

    override fun remove(timerId: TimerId) {
        records.remove(timerId)
    }

    override fun all(): List<MirroredTimer> = records.values.toList()

    override fun clear() = records.clear()
}

/** Records every alarm armed and every cancel, in order, and answers with [result]. */
class FakeTimerScheduler(var result: TimerArmResult = TimerArmResult.Armed) : TimerScheduler {
    val armed = mutableListOf<TimerAlarm>()
    var cancels = 0
        private set

    override fun arm(alarm: TimerAlarm): TimerArmResult {
        if (result == TimerArmResult.Armed) armed += alarm
        return result
    }

    override fun cancel() {
        cancels++
    }
}

/** The missed-timer notice as the shade would hold it: [shown] is null while nothing is posted. */
class FakeTimerMissedNotices : TimerMissedNotices {
    var shown: List<Timer>? = null

    override fun isShowing(): Boolean = shown != null

    override fun post(missed: List<Timer>) {
        shown = missed
    }

    override fun clear() {
        shown = null
    }
}
