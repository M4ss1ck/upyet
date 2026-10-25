package dev.upyet.prototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One switchable variant of a prototype screen: a short key for the pill, a name for a human, and the screen. */
data class PrototypeVariant(val key: String, val name: String, val content: @Composable () -> Unit)

/**
 * Renders one [PrototypeVariant] full screen plus the floating pill that switches between them, so a
 * reviewer can flip A/B/C on a device without rebuilding anything.
 *
 * The pill sits above the bottom navigation bar rather than over it: a shell under review keeps its
 * own tabs usable while the reviewer compares. [controls] is the slot for a prototype's own flags
 * (build a row of [PrototypeControlChip]s); it renders inside the pill so the two never overlap.
 *
 * The pill collapses to a 32 dp handle, because a floating overlay that is always on screen distorts
 * exactly what a prototype is judged on - a bottom bar, a FAB, a list that ends at the edge. Collapsed,
 * the handle keeps only the variant key, so the reviewer can still tell which variant is showing; the
 * variants themselves reserve no space for the pill at all.
 */
@Composable
fun PrototypeVariants(variants: List<PrototypeVariant>, modifier: Modifier = Modifier, controls: (@Composable () -> Unit)? = null) {
    if (variants.isEmpty()) return
    var index by rememberSaveable(variants.size) { mutableIntStateOf(0) }
    // Defaults to expanded: a reviewer opening a prototype needs the switch before they need the view.
    var collapsed by rememberSaveable { mutableStateOf(false) }
    val current = variants[index.coerceIn(variants.indices)]
    Box(modifier.fillMaxSize()) {
        current.content()
        if (collapsed) {
            // The handle sits in the bottom-end corner, clear of the navigation bar inset, where it hides
            // the least of the layout underneath.
            PrototypeHandle(
                key = current.key,
                onExpand = { collapsed = false },
                modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp),
            )
        } else {
            // 92 dp clears the 80 dp navigation bar; navigationBarsPadding() clears the system bar inset
            // that an edge-to-edge window draws underneath it.
            val pillModifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 92.dp)
            PrototypePill(
                label = "${current.key} · ${current.name}",
                onPrevious = { index = (index - 1 + variants.size) % variants.size },
                onNext = { index = (index + 1) % variants.size },
                onCollapse = { collapsed = true },
                controls = controls,
                modifier = pillModifier,
            )
        }
    }
}

/**
 * The single always-present affordance of every prototype: previous, current, next. High contrast
 * against every variant - it is a tool laid on top of the product, not part of it.
 */
@Composable
private fun PrototypePill(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCollapse: () -> Unit,
    controls: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillArrow(glyph = "◀", onClick = onPrevious)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                PillArrow(glyph = "▶", onClick = onNext)
                // The way out of the reviewer's way: shrink the tool instead of hiding it entirely.
                PillArrow(glyph = "▾", onClick = onCollapse)
            }
            if (controls != null) {
                Spacer(Modifier.height(2.dp))
                controls()
            }
        }
    }
}

/** What the pill becomes when collapsed: a 32 dp disc carrying the variant key, tapped to expand again. */
@Composable
private fun PrototypeHandle(key: String, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(32.dp).clip(CircleShape).clickable(onClick = onExpand),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 8.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = key, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** Text glyphs rather than icons: no dependency on which chevron the icon set happened to deprecate. */
@Composable
private fun PillArrow(glyph: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Text(text = glyph, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The same chip, for a flag that has more than two positions: it shows its current value and moves to the
 * next one on every tap. A prototype with four recording states would otherwise need four chips that read
 * "camera failed: on", which is worse than useless.
 */
@Composable
fun PrototypeCycleChip(label: String, value: String, modifier: Modifier = Modifier, onNext: () -> Unit) {
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface
    Surface(
        modifier = modifier.clip(CircleShape).clickable(onClick = onNext),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(contentColor, CircleShape))
            Text(text = "$label: $value", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * A tiny on/off switch that lives inside the pill, for "show me this variant with and without X".
 * The state belongs to the prototype composable, not to this chip, so the variant content can read it.
 */
@Composable
fun PrototypeControlChip(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val contentColor = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.inverseOnSurface
    Surface(
        modifier = modifier.clip(CircleShape).clickable { onCheckedChange(!checked) },
        shape = CircleShape,
        color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.inverseSurface,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(contentColor, CircleShape))
            Text(text = "$label: ${if (checked) "on" else "off"}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
