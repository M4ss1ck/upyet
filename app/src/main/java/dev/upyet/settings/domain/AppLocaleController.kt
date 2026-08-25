package dev.upyet.settings.domain

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.upyet.settings.data.AppLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the per-app language override.
 *
 * The framework owns the value: [LocaleManager] persists it, applies it to the whole process — services and
 * the ringing notification included — and keeps it in sync with the system's own per-app language screen, so
 * nothing about the choice is stored in DataStore.
 *
 * The override exists only from Android 13. The AppCompat backport is deliberately not used: it requires
 * every activity to extend `AppCompatActivity`, which would re-base and re-theme the alarm-critical
 * [dev.upyet.alarm.ringing.RingingActivity], and below API 33 it still would not reach the application
 * context that builds the ringing notification. Older devices follow the device locale.
 */
@Singleton
class AppLocaleController @Inject constructor(@ApplicationContext private val context: Context) {
    val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun current(): AppLanguage {
        // The version guard is inlined at both call sites rather than shared through a helper, so that
        // lint's flow analysis can see it and NewApi stays clean without a suppression.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return AppLanguage.SYSTEM
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return AppLanguage.fromTag(locales[0]?.toLanguageTag())
    }

    /** Setting the locale makes the platform recreate the visible activity, which re-reads every resource. */
    fun set(language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
    }
}
