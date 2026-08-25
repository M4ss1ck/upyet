package dev.upyet.core.ui.theme

import androidx.compose.ui.graphics.Color

// UpYet brand palette. The four anchors come from the brand sheet; everything else is derived
// from them and exists because a real screen needs it (a readable coral for text, tinted
// containers for badges, a warning ramp the Material scheme has no slot for).

/** Ink — headings, night surfaces, the pupil of the mark. */
val Ink = Color(0xFF1B1E4B)
val InkMuted = Color(0xFF6E7499)
val InkFaint = Color(0xFF9AA0BE)

/** Signal Blue — exactly one primary action per screen. */
val SignalBlue = Color(0xFF2563FF)
val SignalBluePressed = Color(0xFF1D4FD8)
val SignalBlueContainer = Color(0xFFE1E9FF)
val OnSignalBlueContainer = Color(0xFF143FB0)

/** Coral — recording, missed, destructive. The bright brand tone and a darker one that passes on white. */
val Coral = Color(0xFFFF6B6B)
val CoralText = Color(0xFFE04E4E)
val CoralContainer = Color(0xFFFFE5E5)
val OnCoralContainer = Color(0xFFC23A3A)

/** Mist — the app background that white cards sit on. */
val Mist = Color(0xFFF2F4F8)
val Paper = Color(0xFFFFFFFF)
val Line = Color(0xFFE4E8F1)
val LineStrong = Color(0xFFC6CCE0)

/** Outcome greens and ambers: dismissed and "worth a look but the alarm still rings". */
val Success = Color(0xFF0FA968)
val SuccessContainer = Color(0xFFDFF5EA)
val OnSuccessContainer = Color(0xFF0B7A4B)
val Warning = Color(0xFFE9930B)
val WarningContainer = Color(0xFFFDF0D9)
val OnWarningContainer = Color(0xFF9A6206)

// Dark theme. Ink deepens into night; the blue lightens so it survives on it.
val Night = Color(0xFF0B0D28)
val NightSurface = Color(0xFF171B45)
val NightSurfaceVariant = Color(0xFF262B62)
val NightOutline = Color(0xFF363C7E)
val NightOnSurface = Color(0xFFEDEFF9)
val NightOnSurfaceVariant = Color(0xFF9AA2D2)
val SkyBlue = Color(0xFF7FA3FF)
val SkyBlueContainer = Color(0xFF23306E)

/**
 * The ringing screen is always dark, whatever the system theme: it is looked at in a dark bedroom by
 * someone who has just woken up. It therefore names its own colors instead of reading the scheme.
 */
object RingingPalette {
    val background = Night
    val surface = NightSurface
    val outline = NightOutline
    val onBackground = Color(0xFFFFFFFF)
    val onBackgroundMuted = Color(0xFFC3C8E4)
    val onBackgroundFaint = Color(0xFF8B93C8)
    val accent = SignalBlue
    val recording = Coral
}
