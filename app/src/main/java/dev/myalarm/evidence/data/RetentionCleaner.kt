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
    suspend fun clean(now: Instant = Instant.now()) {
        val days = settings.settings.first().retention.days ?: return
        val old = segments.selectFinishedBefore(now.minusSeconds(days * 86_400L).toEpochMilli())
        files.deleteAll(old.mapNotNull { it.fileName })
        segments.deleteByIds(old.map { it.id })
    }
}
