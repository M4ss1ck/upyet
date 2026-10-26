package dev.upyet

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.Alarm
import dev.upyet.alarm.domain.AlarmId
import dev.upyet.alarm.domain.AlarmRepository
import dev.upyet.alarm.domain.OccurrenceId
import dev.upyet.alarm.domain.Recurrence
import dev.upyet.alarm.domain.UpcomingAlarm
import dev.upyet.alarm.scheduling.AlarmOccurrenceKind
import dev.upyet.alarm.scheduling.AlarmRescheduler
import dev.upyet.alarm.scheduling.AlarmScheduler
import dev.upyet.alarm.scheduling.SchedulingResult
import dev.upyet.alarm.scheduling.UpcomingAlarmScheduler
import dev.upyet.core.database.EvidenceSegmentDao
import dev.upyet.core.database.EvidenceSegmentEntity
import dev.upyet.evidence.data.EvidenceFileStore
import dev.upyet.evidence.data.RetentionCleaner
import dev.upyet.settings.data.SettingsRepository
import dev.upyet.testing.FakeAlarmMirror
import dev.upyet.testing.FakeAlarmRepository
import dev.upyet.testing.FakeDataStore
import dev.upyet.testing.FakeUserUnlockState
import dev.upyet.testing.FixedTimeProvider
import dev.upyet.timer.scheduling.TimerRearm
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Launch housekeeping is the only supported recovery after a force stop, which the platform does not
 * announce (AGENTS.md §6, §17b). Every step is observed through the collaborators it drives, in one shared
 * log, so both "exactly once" and the order are visible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val events = mutableListOf<String>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun oneLaunchRunsEveryHousekeepingStepExactlyOnce() {
        val housekeeping = Housekeeping(listOf(alarm(1, enabled = true), alarm(2, enabled = false)))

        housekeeping.newViewModel()

        assertThat(housekeeping.scheduler.scheduled).containsExactly(AlarmId(1))
        assertThat(housekeeping.scheduler.cancelled).containsExactly(AlarmId(2) to AlarmOccurrenceKind.MAIN)
        assertThat(events.count { it == UPCOMING }).isEqualTo(1)
        assertThat(events.count { it == TIMER }).isEqualTo(1)
        assertThat(events.count { it == RETENTION }).isEqualTo(1)
    }

    @Test fun alarmsAreRecoveredBeforeTimersAndRetention() {
        val housekeeping = Housekeeping(listOf(alarm(1, enabled = true)))

        housekeeping.newViewModel()

        // Alarm reliability outranks evidence: a slow or failing retention pass must not delay recovery.
        assertThat(events).containsExactly("schedule:1", UPCOMING, TIMER, RETENTION).inOrder()
    }

    @Test fun relaunchingReschedulesTheSameAlarmIdentityOncePerLaunch() {
        val housekeeping = Housekeeping(listOf(alarm(1, enabled = true)))

        housekeeping.newViewModel()
        housekeeping.newViewModel()

        // Each launch asks once for the same alarm id and kind. That the second request replaces the first
        // rather than adding one is AlarmPendingIntents' deterministic identity, pinned in its own test.
        assertThat(housekeeping.scheduler.scheduled).containsExactly(AlarmId(1), AlarmId(1))
        assertThat(events.count { it == TIMER }).isEqualTo(2)
    }

    @Test fun aFailedScheduleIsReportedAndTheRestOfHousekeepingStillRuns() {
        val housekeeping = Housekeeping(
            listOf(alarm(1, enabled = true)),
            result = SchedulingResult.Failed(SecurityException("denied")),
        )

        housekeeping.newViewModel()

        assertThat(housekeeping.scheduler.scheduled).containsExactly(AlarmId(1))
        assertThat(events).containsAtLeast(TIMER, RETENTION).inOrder()
    }

    @Test fun aThrowingAlarmReadIsLoggedAndTimersAndRetentionStillRun() {
        val housekeeping = Housekeeping(emptyList(), repository = UnreadableAlarms)

        housekeeping.newViewModel()

        assertThat(housekeeping.scheduler.scheduled).isEmpty()
        assertThat(events).containsExactly(TIMER, RETENTION).inOrder()
    }

    @Test fun constructingTheViewModelDoesNoHousekeepingOnTheCallingThread() {
        val housekeeping = Housekeeping(listOf(alarm(1, enabled = true)))
        val background = StandardTestDispatcher()

        housekeeping.newViewModel(background)

        // MainActivity.onCreate builds this ViewModel before the first frame; nothing may run there, not
        // even the diagnostic log line, which writes a file synchronously.
        assertThat(events).isEmpty()
        background.scheduler.advanceUntilIdle()
        assertThat(events).containsExactly("schedule:1", UPCOMING, TIMER, RETENTION).inOrder()
    }

    private inner class Housekeeping(
        alarms: List<Alarm>,
        result: SchedulingResult = SchedulingResult.Scheduled,
        repository: AlarmRepository = FakeAlarmRepository(alarms),
    ) {
        val scheduler = RecordingScheduler(result)
        private val time = FixedTimeProvider()
        private val settings = SettingsRepository(FakeDataStore())
        private val rescheduler = AlarmRescheduler(
            repository,
            scheduler,
            time,
            FakeUserUnlockState(),
            FakeAlarmMirror(),
            settings,
            RecordingUpcoming(),
        )
        private val retention = RetentionCleaner(settings, RecordingSegments(), NoFiles, time)

        fun newViewModel(dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) =
            MainViewModel(retention, rescheduler, TimerRearm { events += TIMER }, dispatcher)
    }

    private inner class RecordingScheduler(private val result: SchedulingResult) : AlarmScheduler {
        val scheduled = mutableListOf<AlarmId>()
        val cancelled = mutableListOf<Pair<AlarmId, AlarmOccurrenceKind>>()

        override fun schedule(
            alarm: Alarm,
            triggerAt: Instant,
            kind: AlarmOccurrenceKind,
            parentOccurrenceId: OccurrenceId?,
            snoozesRemaining: Int,
            chainStartedAtMillis: Long,
        ): SchedulingResult {
            scheduled += alarm.id
            events += "schedule:${alarm.id.value}"
            return result
        }

        override fun cancel(alarmId: AlarmId, kind: AlarmOccurrenceKind) {
            cancelled += alarmId to kind
        }

        override fun nextScheduledTrigger(): Instant? = null
    }

    private inner class RecordingUpcoming : UpcomingAlarmScheduler {
        override fun sync(upcoming: UpcomingAlarm?, now: Instant) {
            events += UPCOMING
        }

        override fun cancelNotification() = Unit
    }

    private inner class RecordingSegments : EvidenceSegmentDao {
        override fun observeForOccurrence(occurrenceId: Long): Flow<List<EvidenceSegmentEntity>> = emptyFlow()
        override suspend fun insert(segment: EvidenceSegmentEntity): Long = 0
        override suspend fun upsert(segment: EvidenceSegmentEntity) = Unit
        override suspend fun selectFinishedBefore(cutoffMillis: Long): List<EvidenceSegmentEntity> {
            events += RETENTION
            return emptyList()
        }
        override suspend fun deleteByIds(ids: List<Long>) = Unit
    }

    private object UnreadableAlarms : AlarmRepository {
        override fun observeAlarms(): Flow<List<Alarm>> = flow { throw IllegalStateException("database unavailable") }
        override suspend fun getAlarm(id: AlarmId): Alarm? = error("not used")
        override suspend fun upsert(alarm: Alarm): AlarmId = error("not used")
        override suspend fun setEnabled(id: AlarmId, enabled: Boolean) {
            error("not used")
        }
        override suspend fun setSkipNextOn(id: AlarmId, date: LocalDate?) {
            error("not used")
        }
        override suspend fun delete(id: AlarmId) {
            error("not used")
        }
    }

    private object NoFiles : EvidenceFileStore {
        override fun newEvidenceFile(): File = error("not used")
        override fun resolve(fileName: String): File = error("not used")
        override fun resolveThumbnail(fileName: String): File = error("not used")
        override fun delete(fileName: String): Boolean = false
        override fun deleteAll(fileNames: Iterable<String>) = Unit
    }

    private fun alarm(id: Long, enabled: Boolean) = Alarm(
        AlarmId(id),
        LocalTime.of(11, 0),
        enabled,
        "Wake up",
        Recurrence.Daily,
        null,
        true,
        true,
        9,
        false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private companion object {
        const val UPCOMING = "upcoming"
        const val TIMER = "timer"
        const val RETENTION = "retention"
    }
}
