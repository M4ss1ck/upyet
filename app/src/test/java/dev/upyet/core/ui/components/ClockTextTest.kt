package dev.upyet.core.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClockTextTest {
    private val face = SpanStyle(color = Color.Red)
    private val marker = SpanStyle(color = Color.Blue)

    /** The style each character ends up carrying, as "f" for the clock face and "m" for the marker. */
    private fun styleRuns(text: String): String {
        val annotated = splitClockFace(text, face, marker)
        return text.indices.joinToString("") { index ->
            val style = annotated.spanStyles.single { index >= it.start && index < it.end }.item
            if (style == face) "f" else "m"
        }
    }

    @Test fun theMarkersOwnFullStopsAreMarkerSized() {
        // Spanish and other locales write the marker with full stops. They are part of the letters,
        // not the clock face, so styling them as face digits left large dots beside small letters.
        assertThat(styleRuns("7:30 a. m.")).isEqualTo("ffffmmmmmm")
    }

    @Test fun aFullStopBetweenDigitsIsPartOfTheFace() {
        // Danish and Finnish separate the hour from the minutes with a full stop.
        assertThat(styleRuns("07.30")).isEqualTo("fffff")
    }

    @Test fun theMarkerIsSeparatedFromTheDigitsInTheUsualCase() {
        assertThat(styleRuns("7:30 AM")).isEqualTo("ffffmmm")
    }
}
