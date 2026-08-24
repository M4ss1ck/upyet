package dev.myalarm.evidence.data

import dev.myalarm.core.database.EvidenceSegmentDao
import dev.myalarm.settings.data.RetentionPolicy
import dev.myalarm.settings.data.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetentionCleaner @Inject constructor(
    private val settings: SettingsRepository,
    private val segments: EvidenceSegmentDao,
    private val files: EvidenceFileStore,
) {
    /** Deletes expired evidence metadata and files, but never touches an active recording. */
    suspend fun clean(now: Instant = Instant.now()) {
        val retention = settings.settings.first().retention
        val days = retention.days ?: return
        val old = selectSegmentsForRetention(
            segments.selectFinishedBefore(now.minusSeconds(days * 86_400L).toEpochMilli()),
            retention,
            now.minusSeconds(days * 86_400L).toEpochMilli(),
        )
        files.deleteAll(old.mapNotNull { it.fileName })
        segments.deleteByIds(old.map { it.id })
    }
}
