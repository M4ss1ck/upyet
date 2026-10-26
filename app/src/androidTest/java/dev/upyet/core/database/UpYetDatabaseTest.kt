package dev.upyet.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.upyet.timer.data.RoomTimerRepository
import dev.upyet.timer.domain.Timer
import dev.upyet.timer.domain.TimerAnchor
import dev.upyet.timer.domain.TimerId
import dev.upyet.timer.domain.TimerRunState
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
    fun aTimerRoundTripsThroughTheRepositoryInEveryRunState() = runBlocking<Unit> {
        val repository = RoomTimerRepository(database.timerDao())
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
        val repository = RoomTimerRepository(database.timerDao())
        val createdAt = Instant.parse("2026-10-26T07:00:00Z")
        val id = repository.upsert(timerWith(TimerId(0), "Pasta", true, TimerRunState.Idle, createdAt))

        repository.upsert(timerWith(id, "Eggs", false, TimerRunState.Idle, createdAt))

        val expected = timerWith(id, "Eggs", false, TimerRunState.Idle, createdAt)
        assertThat(repository.getTimer(id)).isEqualTo(expected)
        assertThat(repository.observeTimers().first()).containsExactly(expected)
    }

    @Test
    fun deleteRemovesTheTimer() = runBlocking<Unit> {
        val repository = RoomTimerRepository(database.timerDao())
        val id =
            repository.upsert(
                timerWith(TimerId(0), "Pasta", true, TimerRunState.Idle, Instant.parse("2026-10-26T07:00:00Z")),
            )

        repository.delete(id)

        assertThat(repository.getTimer(id)).isNull()
    }

    @Test
    fun timersAreListedInCreationOrder() = runBlocking<Unit> {
        val repository = RoomTimerRepository(database.timerDao())
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
}
