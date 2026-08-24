package dev.myalarm.evidence.data

import dev.myalarm.core.database.EvidenceSegmentDao
import dev.myalarm.core.time.TimeProvider
import dev.myalarm.settings.data.RetentionPolicy
import dev.myalarm.settings.data.SettingsRepository
import dev.myalarm.settings.data.cutoffFrom
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetentionCleaner @Inject constructor(
    private val settings: SettingsRepository,
    private val segments: EvidenceSegmentDao,
    private val files: EvidenceFileStore,
    private val timeProvider: TimeProvider,
) {
    /**
     * Deletes expired evidence metadata and files. Segments still in REQUESTED or RECORDING state are
     * excluded by the query and by [selectSegmentsForRetention], so an in-progress recording can never lose
     * its file underneath it.
     *
     * @return the number of segments removed.
     */
    suspend fun clean(now: Instant = timeProvider.now()): Int {
        val retention = settings.settings.first().retention
        val cutoff = retention.cutoffFrom(now) ?: return 0
        val old = selectSegmentsForRetention(
            segments.selectFinishedBefore(cutoff.toEpochMilli()),
            retention,
            cutoff.toEpochMilli(),
        )
        files.deleteAll(old.mapNotNull { it.fileName })
        segments.deleteByIds(old.map { it.id })
        return old.size
    }
}
