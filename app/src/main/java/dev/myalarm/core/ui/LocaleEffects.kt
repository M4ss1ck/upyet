package dev.myalarm.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * The current locale read through the composition, so a locale change recomposes formatted times.
 * Reading Locale.getDefault() directly inside a composable is not observable.
 */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Convenience for composables that need a formatter bound to the current locale. */
@Composable
fun <T> rememberLocalized(create: (Locale) -> T): T {
    val locale = currentLocale()
    return remember(locale) { create(locale) }
}
