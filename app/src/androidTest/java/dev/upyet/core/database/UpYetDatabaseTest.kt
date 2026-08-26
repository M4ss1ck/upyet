package dev.upyet.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
}
