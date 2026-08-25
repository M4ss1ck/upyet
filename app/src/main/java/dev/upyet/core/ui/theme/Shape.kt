package dev.upyet.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Generous, even rounding - the mark is drawn with round caps and a squircle icon, and square
// corners look borrowed next to it.
val UpYetShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Controls: 52 dp tall with a 16 dp radius; the two ringing buttons are deliberately bigger. */
val ControlHeight = 52.dp
val MinTouchTarget = 48.dp
