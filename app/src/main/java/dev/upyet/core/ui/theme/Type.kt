package dev.upyet.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

// UpYet ships no font files: the brand faces are not redistributable from here, and downloadable
// fonts would mean a network round trip in an app that deliberately has none. The brand instead
// lives in the mark, the palette and this ramp - weight, size and tracking on the platform sans.
private val Brand = FontFamily.SansSerif

/** Clock faces: heavy, tight, and tabular so digits do not shuffle as the minute changes. */
val ClockLarge =
    TextStyle(fontFamily = Brand, fontWeight = FontWeight.ExtraBold, fontSize = 84.sp, lineHeight = 88.sp, letterSpacing = (-2).sp)
val ClockMedium =
    TextStyle(fontFamily = Brand, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1).sp)
val ClockSmall =
    TextStyle(fontFamily = Brand, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.8).sp)

/**
 * The style Material's own components reach for through `displayLarge` - notably the TimePicker's
 * hour and minute boxes. It is deliberately NOT [ClockLarge]: at 84 sp the digits overflow those
 * squares, and the wider the digits the more width they take from the AM/PM selector beside them. The ringing screen asks for [ClockLarge] explicitly instead.
 *
 * lineHeight matches fontSize and the line box is centred, so a digit sits in the middle of
 * whatever container Material puts it in rather than riding low on its baseline.
 */
val ClockDisplay = TextStyle(
    fontFamily = Brand,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 44.sp,
    lineHeight = 44.sp,
    letterSpacing = (-1).sp,
    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None),
)

/** The meridiem that trails a clock face, and the uppercase section labels above every group. */
val Meridiem = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp)
val SectionLabel = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.5.sp)

val UpYetTypography = Typography(
    displayLarge = ClockDisplay,
    displayMedium = ClockMedium,
    displaySmall = ClockSmall,
    headlineMedium = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Brand, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Brand, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(
        fontFamily = Brand,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Unspecified,
    ),
    bodySmall = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = SectionLabel,
)
