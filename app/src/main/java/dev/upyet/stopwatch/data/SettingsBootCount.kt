package dev.upyet.stopwatch.data

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.stopwatch.domain.BootCount
import javax.inject.Inject

/** Reads `Settings.Global.BOOT_COUNT`. Some builds omit it, so an absent or negative value is null, not zero. */
class SettingsBootCount @Inject constructor(@ApplicationContext private val context: Context) : BootCount {
    override fun current(): Int? = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }
}
