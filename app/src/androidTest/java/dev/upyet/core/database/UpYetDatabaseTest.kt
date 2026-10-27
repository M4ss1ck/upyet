package dev.upyet.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.upyet.core.directboot.MirroredTimer
import dev.upyet.core.directboot.TimerMirror
import dev.upyet.timer.data.RoomTimerRepository
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
import dev.upyet.timer.scheduling.RunningNotice
import dev.upyet.timer.scheduling.TimerAlarm
import dev.upyet.timer.scheduling.TimerAlarmSync
import dev.upyet.timer.scheduling.TimerArmResult
import dev.upyet.timer.scheduling.TimerMissedNotices
import dev.upyet.timer.scheduling.TimerRunningNotices
import dev.upyet.timer.scheduling.TimerScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class UpYetDatabaseTest {
    private lateinit var database: UpYetDatabase

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider
                        .getApplicationContext(),
                    UpYetDatabase::class.java,
                ).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun roundTripAndDeletionRules() = runBlocking {
        val alarmId =
            database.alarmDao().upsert(
                AlarmEntity(
                    0,
                    450,
                    true,
                    "",
                    "ONE_TIME",
                    0,
                    null,
                    true,
                    true,
                    9,
                    true,
                    null,
                    1,
                    2,
                ),
            )
        val occurrenceId =
            database.occurrenceDao().insert(
                AlarmOccurrenceEntity(
                    0,
                    alarmId,
                    3,
                    null,
                    null,
                    null,
                    "RINGING",
                    null,
                ),
            )
        val segmentId =
            database.evidenceSegmentDao().insert(
                EvidenceSegmentEntity(
                    0,
                    occurrenceId,
                    4,
                    null,
                    null,
                    null,
                    "file.mp4",
                    null,
                    null,
                    "RECORDED",
                    null,
                ),
            )
        assertThat(
            database.alarmDao().observeAll().first(),
        ).hasSize(1)
        assertThat(
            database
                .occurrenceDao()
                .observeById(
                    occurrenceId,
                ).first(),
        ).isNotNull()
        assertThat(
            database
                .evidenceSegmentDao()
                .observeForOccurrence(occurrenceId)
                .first()
                .single()
                .id,
        ).isEqualTo(segmentId)

        database.alarmDao().deleteById(alarmId)
        assertThat(
            database
                .occurrenceDao()
                .observeById(
                    occurrenceId,
                ).first(),
        ).isNotNull()
        database.occurrenceDao().deleteById(
            occurrenceId,
        )
        assertThat(
            database
                .evidenceSegmentDao()
                .observeForOccurrence(
                    occurrenceId,
                ).first(),
        ).isEmpty()
    }

    @Test
    fun aTimerOccurrenceReadsBackWithItsAttributionAndDeletesItsSegments() = runBlocking<Unit> {
        val occurrenceId =
            database.occurrenceDao().insert(
                AlarmOccurrenceEntity(
                    id = 0,
                    alarmId = AlarmOccurrenceEntity.NO_ALARM_ID,
                    scheduledFor = 3,
                    triggeredAt = 3,
                    activityVisibleAt = null,
                    dismissedAt = null,
                    outcome = "RINGING",
                    parentOccurrenceId = null,
                    source = "TIMER",
                    timerAttribution = "1:4:Pasta",
                ),
            )
        database.evidenceSegmentDao().insert(
            EvidenceSegmentEntity(0, occurrenceId, 4, null, null, null, "timer.mp4", null, null, "RECORDED", null),
        )

        val read = database.occurrenceDao().observeById(occurrenceId).first()
        assertThat(read?.source).isEqualTo("TIMER")
        assertThat(read?.alarmId).isEqualTo(AlarmOccurrenceEntity.NO_ALARM_ID)
        assertThat(read?.timerAttribution).isEqualTo("1:4:Pasta")

        database.occurrenceDao().updateTimerAttribution(occurrenceId, "1:4:Pasta2:3:Tea")
        assertThat(database.occurrenceDao().observeById(occurrenceId).first()?.timerAttribution).isEqualTo("1:4:Pasta2:3:Tea")

        database.occurrenceDao().deleteById(occurrenceId)
        assertThat(database.evidenceSegmentDao().observeForOccurrence(occurrenceId).first()).isEmpty()
    }

    /** The orphan sweep closes only timer rings a dead process left open: alarms and finished timers stay as they are. */
    @Test
    fun interruptRingingTimerOccurrencesLeavesAlarmsAndFinishedTimersAlone() = runBlocking<Unit> {
        val dao = database.occurrenceDao()
        fun row(source: String, outcome: String, alarmId: Long) = AlarmOccurrenceEntity(
            alarmId = alarmId,
            scheduledFor = 1_000,
            triggeredAt = 1_000,
            activityVisibleAt = null,
            dismissedAt = null,
            outcome = outcome,
            parentOccurrenceId = null,
            source = source,
            timerAttribution = if (source == "TIMER") "1:3:Tea" else null,
        )
        val orphan = dao.insert(row("TIMER", "RINGING", AlarmOccurrenceEntity.NO_ALARM_ID))
        val ringingAlarm = dao.insert(row("ALARM", "RINGING", 1))
        val stoppedTimer = dao.insert(row("TIMER", "STOPPED", AlarmOccurrenceEntity.NO_ALARM_ID))

        assertThat(dao.interruptRingingTimerOccurrences()).isEqualTo(1)

        assertThat(dao.observeById(orphan).first()?.outcome).isEqualTo("INTERRUPTED")
        assertThat(dao.observeById(orphan).first()?.dismissedAt).isNull()
        assertThat(dao.observeById(ringingAlarm).first()?.outcome).isEqualTo("RINGING")
        assertThat(dao.observeById(stoppedTimer).first()?.outcome).isEqualTo("STOPPED")
    }

    /** Deleting a timer forgets the timer, not its evidence: attribution is text, so nothing cascades (AGENTS.md §10, §12). */
    @Test
    fun deletingATimerKeepsItsTimerOccurrences() = runBlocking<Unit> {
        val timerId =
            database.timerDao().upsert(
                TimerEntity(
                    id = 0,
                    lengthMillis = 600_000,
                    label = "Pasta",
                    ringtoneUri = null,
                    evidenceEnabled = true,
                    runState = "IDLE",
                    remainingAtAnchorMillis = null,
                    anchorElapsedRealtimeMillis = null,
                    anchorWallClockMillis = null,
                    createdAt = 7,
                ),
            )
        val occurrenceId =
            database.occurrenceDao().insert(
                AlarmOccurrenceEntity(
                    id = 0,
                    alarmId = AlarmOccurrenceEntity.NO_ALARM_ID,
                    scheduledFor = 3,
                    triggeredAt = 3,
                    activityVisibleAt = null,
                    dismissedAt = null,
                    outcome = "STOPPED",
                    parentOccurrenceId = null,
                    source = "TIMER",
                    timerAttribution = "$timerId:4:Pasta",
                ),
            )
        database.evidenceSegmentDao().insert(
            EvidenceSegmentEntity(0, occurrenceId, 4, null, null, null, "timer.mp4", null, null, "RECORDED", null),
        )

        database.timerDao().deleteById(timerId)

        assertThat(database.timerDao().findById(timerId)).isNull()
        val kept = database.occurrenceDao().observeById(occurrenceId).first()
        assertThat(kept?.source).isEqualTo("TIMER")
        assertThat(kept?.timerAttribution).isEqualTo("$timerId:4:Pasta")
        assertThat(database.evidenceSegmentDao().observeForOccurrence(occurrenceId).first()).hasSize(1)
    }

    @Test
    fun aTimerRoundTripsThroughTheRepositoryInEveryRunState() = runBlocking<Unit> {
        val repository = timerRepository()
        val createdAt = Instant.parse("2026-10-26T07:00:00Z")
        val runStates =
            listOf(
                TimerRunState.Idle,
                TimerRunState.Running(
                    Duration.ofSeconds(425),
                    TimerAnchor(Duration.ofMillis(123_456), Instant.parse("2026-10-26T08:00:00Z")),
                ),
                TimerRunState.Paused(Duration.ofSeconds(30)),
                TimerRunState.Ringing,
                TimerRunState.Missed,
            )
        runStates.forEach { runState ->
            val saved = timerWith(TimerId(0), "Pasta | ñ", true, runState, createdAt)
            val id = repository.upsert(saved)
            assertThat(repository.getTimer(id)).isEqualTo(timerWith(id, "Pasta | ñ", true, runState, createdAt))
        }
    }

    @Test
    fun theEvidenceFlagAndLabelSurviveAnUpdate() = runBlocking<Unit> {
        val repository = timerRepository()
        val createdAt = Instant.parse("2026-10-26T07:00:00Z")
        val id = repository.upsert(timerWith(TimerId(0), "Pasta", true, TimerRunState.Idle, createdAt))

        repository.upsert(timerWith(id, "Eggs", false, TimerRunState.Idle, createdAt))

        val expected = timerWith(id, "Eggs", false, TimerRunState.Idle, createdAt)
        assertThat(repository.getTimer(id)).isEqualTo(expected)
        assertThat(repository.observeTimers().first()).containsExactly(expected)
    }

    @Test
    fun deleteRemovesTheTimer() = runBlocking<Unit> {
        val repository = timerRepository()
        val id =
            repository.upsert(
                timerWith(TimerId(0), "Pasta", true, TimerRunState.Idle, Instant.parse("2026-10-26T07:00:00Z")),
            )

        repository.delete(id)

        assertThat(repository.getTimer(id)).isNull()
    }

    @Test
    fun timersAreListedInCreationOrder() = runBlocking<Unit> {
        val repository = timerRepository()
        val laterId =
            repository.upsert(
                timerWith(TimerId(0), "Later", true, TimerRunState.Idle, Instant.parse("2026-10-26T09:00:00Z")),
            )
        val earlierId =
            repository.upsert(
                timerWith(TimerId(0), "Earlier", true, TimerRunState.Idle, Instant.parse("2026-10-26T08:00:00Z")),
            )

        val listed = repository.observeTimers().first()

        assertThat(listed.map { it.id }).containsExactly(earlierId, laterId).inOrder()
    }

    private fun timerWith(id: TimerId, label: String, evidenceEnabled: Boolean, runState: TimerRunState, createdAt: Instant): Timer =
        Timer.restore(
            id = id,
            length = Duration.ofMinutes(10),
            label = label,
            ringtoneUri = "content://media/internal/audio/media/9",
            evidenceEnabled = evidenceEnabled,
            runState = runState,
            createdAt = createdAt,
        )

    /** Room is what these tests exercise; the mirror and the alarm are covered by their own tests. */
    private fun timerRepository() = RoomTimerRepository(
        database.timerDao(),
        object : TimerMirror {
            override fun put(record: MirroredTimer) = Unit

            override fun markReconciled(timerId: TimerId) = Unit

            override fun remove(timerId: TimerId) = Unit

            override fun all(): List<MirroredTimer> = emptyList()

            override fun clear() = Unit
        },
        TimerAlarmSync(
            object : TimerScheduler {
                override fun arm(alarm: TimerAlarm): TimerArmResult = TimerArmResult.Armed

                override fun cancel() = Unit
            },
            object : TimerMissedNotices {
                override fun isShowing(): Boolean = false

                override fun post(missed: List<Timer>) = Unit

                override fun clear() = Unit
            },
            { TimerAnchor(Duration.ZERO, Instant.EPOCH) },
            object : TimerRunningNotices {
                override fun show(notice: RunningNotice.Show) = Unit

                override fun cancel() = Unit
            },
        ),
    )
}
