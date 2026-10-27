package dev.upyet.timer.data

import com.google.common.truth.Truth.assertThat
import dev.upyet.core.database.TimerDao
import dev.upyet.core.database.TimerEntity
import dev.upyet.core.directboot.MirroredTimer
import dev.upyet.core.directboot.TimerMirror
import dev.upyet.testing.FakeTimerMirror
import dev.upyet.testing.FakeTimerMissedNotices
import dev.upyet.testing.FakeTimerRunningNotices
import dev.upyet.testing.FakeTimerScheduler
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerTransition
import dev.upyet.timer.scheduling.TimerAlarm
import dev.upyet.timer.scheduling.TimerAlarmSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** AGENTS.md §6a: every mutation re-arms, and the device-protected mirror follows Room (ADR 0008). */
class RoomTimerRepositoryTest {
    private val now = TimerAnchor(Duration.ofMinutes(10), Instant.parse("2026-10-26T08:00:00Z"))
    private val dao = FakeTimerDao()
    private val mirror = FakeTimerMirror()
    private val scheduler = FakeTimerScheduler()
    private val repository =
        RoomTimerRepository(dao, mirror, TimerAlarmSync(scheduler, FakeTimerMissedNotices(), { now }, FakeTimerRunningNotices()))

    private fun newTimer(minutes: Long) =
        Timer.create(TimerId(0), Duration.ofMinutes(minutes), "Tea", null, false, Instant.parse("2026-10-26T07:00:00Z"))

    private fun Timer.started() = (start(now) as TimerTransition.Applied).timer

    @Test
    fun startingANewTimerMirrorsItUnderItsAssignedIdAndArmsIt() = runTest {
        val id = repository.upsert(newTimer(5).started())

        assertThat(id).isEqualTo(TimerId(1))
        assertThat(mirror.all().single().timerId).isEqualTo(TimerId(1))
        assertThat(
            scheduler.armed.single(),
        ).isEqualTo(TimerAlarm(TimerId(1), Instant.parse("2026-10-26T08:05:00Z"), Duration.ofMinutes(15)))
    }

    @Test
    fun aMirrorWriteThatFailsStillReArms() = runTest {
        val failingMirror = object : TimerMirror by FakeTimerMirror() {
            override fun put(record: MirroredTimer) = throw IOException("device-protected storage unavailable")
        }
        val repository =
            RoomTimerRepository(dao, failingMirror, TimerAlarmSync(scheduler, FakeTimerMissedNotices(), { now }, FakeTimerRunningNotices()))

        val thrown = runCatching { repository.upsert(newTimer(5).started()) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IOException::class.java)
        assertThat(scheduler.armed.single().timerId).isEqualTo(TimerId(1))
    }

    @Test
    fun pausingTheOnlyRunningTimerCancelsTheAlarm() = runTest {
        val id = repository.upsert(newTimer(5).started())
        val paused = (repository.getTimer(id)!!.pause(now) as TimerTransition.Applied).timer

        repository.upsert(paused)

        assertThat(scheduler.cancels).isEqualTo(1)
        assertThat(mirror.all().single().state.name).isEqualTo("PAUSED")
    }

    @Test
    fun deletingATimerRemovesItsMirrorAndReArmsForTheNextOne() = runTest {
        val first = repository.upsert(newTimer(5).started())
        repository.upsert(newTimer(9).started())

        repository.delete(first)

        assertThat(mirror.all().map { it.timerId }).containsExactly(TimerId(2))
        assertThat(scheduler.armed.last().timerId).isEqualTo(TimerId(2))
    }
}

/** Room's REPLACE upsert: id 0 is assigned the next row id, any other id replaces that row. */
private class FakeTimerDao : TimerDao {
    private val rows = MutableStateFlow(emptyMap<Long, TimerEntity>())
    private var nextId = 1L

    override fun observeAll(): Flow<List<TimerEntity>> = rows.map { it.values.sortedWith(compareBy({ it.createdAt }, { it.id })) }

    override suspend fun findById(id: Long): TimerEntity? = rows.value[id]

    override suspend fun upsert(timer: TimerEntity): Long {
        val id = if (timer.id == 0L) nextId++ else timer.id
        rows.value = rows.value + (id to timer.copy(id = id))
        return id
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value - id
    }
}
