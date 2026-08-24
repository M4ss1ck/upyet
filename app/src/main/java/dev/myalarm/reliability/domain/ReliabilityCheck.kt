package dev.myalarm.reliability.domain

import androidx.annotation.StringRes
import java.time.Instant

enum class ReliabilityStatus { OK, WARNING, BLOCKED }

data class ReliabilityCheck(
    val id: String,
    @StringRes val titleRes: Int,
    val status: ReliabilityStatus,
    @StringRes val explanationRes: Int,
    val value: Instant? = null,
    val settingsIntent: android.content.Intent? = null,
)
