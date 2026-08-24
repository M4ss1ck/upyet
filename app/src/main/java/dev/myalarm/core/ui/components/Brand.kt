package dev.myalarm.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.myalarm.R
import dev.myalarm.core.ui.theme.Ink
import dev.myalarm.core.ui.theme.SignalBlue

/** The eye-with-clock mark on its own. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 30.dp) {
    Image(
        painter = painterResource(R.drawable.ic_brand_mark),
        contentDescription = stringResource(R.string.brand_mark_description),
        modifier = modifier.size(width = size * 0.917f, height = size),
    )
}

/**
 * The wordmark. UpYet has no bundled typeface - the two-tone split carries the brand instead, so
 * it renders correctly at any system font and scales with the user's font size setting.
 */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier, fontSize: androidx.compose.ui.unit.TextUnit = 26.sp) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Ink)) { append("Up") }
            withStyle(SpanStyle(color = SignalBlue)) { append("Yet") }
        },
        style = MaterialTheme.typography.headlineMedium.copy(fontSize = fontSize, fontWeight = FontWeight.ExtraBold),
        modifier = modifier,
    )
}

/** Mark plus wordmark, the lockup that opens the alarm list. */
@Composable
fun BrandLockup(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandMark()
        BrandWordmark()
    }
}
