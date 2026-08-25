package dev.upyet.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.isSpecified
import dev.upyet.core.ui.rememberLocalized
import dev.upyet.core.ui.theme.Meridiem
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A clock face where the digits are large and any AM/PM marker is small and muted.
 *
 * The two are one text node, not two composables, so the accessible reading and the value a test
 * matches stay the whole localised time ("7:30 AM"), and locales that put the marker first or omit
 * it entirely need no special case.
 */
@Composable
fun ClockText(
    time: LocalTime,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    meridiemColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val formatter = rememberLocalized { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(it) }
    // The marker is sized against the face rather than fixed, so it stays in proportion whether this is
    // a 32 sp row on the list or the 84 sp face on the ringing screen.
    val markerSize = if (style.fontSize.isSpecified) style.fontSize * MERIDIEM_RATIO else Meridiem.fontSize
    val text = remember(time, formatter, color, meridiemColor, markerSize) {
        splitClockFace(
            formatter.format(time),
            SpanStyle(color = color),
            Meridiem.toSpanStyle().copy(color = meridiemColor, fontSize = markerSize),
        )
    }
    // The caller's style is passed through untouched. Merging LocalTextStyle over it let the ambient
    // body size win, which silently shrank every clock face in the app to running-text size.
    Text(text, modifier, style = style)
}

private const val MERIDIEM_RATIO = 0.30f

private fun isFaceCharacter(character: Char) = character.isDigit() || character == ':' || character == '.'

/** Splits a formatted time into runs of clock digits and everything else (the marker and its space). */
internal fun splitClockFace(text: String, face: SpanStyle, marker: SpanStyle): AnnotatedString = buildAnnotatedString {
    var start = 0
    while (start < text.length) {
        val isFace = isFaceCharacter(text[start])
        var end = start
        while (end < text.length && isFaceCharacter(text[end]) == isFace) end++
        // The run is appended verbatim, separator space included: trimming it would change the text
        // a screen reader announces and the string a UI test matches.
        withStyle(if (isFace) face else marker) { append(text, start, end) }
        start = end
    }
}
