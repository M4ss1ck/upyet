package dev.upyet.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Dynamic color is deliberately not used: UpYet has a brand, and an alarm the user recognises at
// 6:40 am by its blue should not be repainted by whatever wallpaper they set last week.
private val LightColors = lightColorScheme(
    primary = SignalBlue,
    onPrimary = Paper,
    primaryContainer = SignalBlueContainer,
    onPrimaryContainer = OnSignalBlueContainer,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Mist,
    onSecondaryContainer = Ink,
    tertiary = Success,
    onTertiary = Paper,
    tertiaryContainer = SuccessContainer,
    onTertiaryContainer = OnSuccessContainer,
    error = CoralText,
    onError = Paper,
    errorContainer = CoralContainer,
    onErrorContainer = OnCoralContainer,
    background = Mist,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Mist,
    surfaceContainerHigh = Mist,
    surfaceContainerHighest = Line,
    outline = LineStrong,
    outlineVariant = Line,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = SkyBlue,
)

private val DarkColors = darkColorScheme(
    primary = SignalBlue,
    onPrimary = Paper,
    primaryContainer = SkyBlueContainer,
    onPrimaryContainer = SkyBlue,
    secondary = SkyBlue,
    onSecondary = Night,
    secondaryContainer = NightSurfaceVariant,
    onSecondaryContainer = NightOnSurface,
    tertiary = Success,
    onTertiary = Night,
    tertiaryContainer = Color(0xFF10462F),
    onTertiaryContainer = Color(0xFF8DE7BE),
    error = Coral,
    onError = Night,
    errorContainer = Color(0xFF5A1F1F),
    onErrorContainer = Color(0xFFFFC9C9),
    background = Night,
    onBackground = NightOnSurface,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightOnSurfaceVariant,
    surfaceContainerLowest = Night,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceVariant,
    surfaceContainerHighest = NightSurfaceVariant,
    outline = NightOutline,
    outlineVariant = NightSurfaceVariant,
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    inversePrimary = SignalBlue,
)

/**
 * The two roles Material has no slot for: a warning that is not an error (the alarm still rings)
 * and the bright recording coral, which is a signal light rather than error text.
 */
data class UpYetExtraColors(val warning: Color, val warningContainer: Color, val onWarningContainer: Color, val recording: Color)

private val LightExtras = UpYetExtraColors(Warning, WarningContainer, OnWarningContainer, Coral)
private val DarkExtras = UpYetExtraColors(Warning, Color(0xFF4A3406), Color(0xFFF7CE87), Coral)

private val LocalUpYetExtraColors = staticCompositionLocalOf { LightExtras }

@Composable
fun UpYetTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUpYetExtraColors provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = UpYetTypography,
            shapes = UpYetShapes,
            content = content,
        )
    }
}

/** Brand colors that live beside the Material scheme rather than inside it. */
val MaterialTheme.extraColors: UpYetExtraColors
    @Composable @ReadOnlyComposable
    get() = LocalUpYetExtraColors.current
