package dev.upyet.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.upyet.core.ui.theme.ControlHeight

/**
 * The three button roles UpYet uses. Material's defaults are a 40 dp pill; these are 52 dp with the
 * brand's 16 dp radius, which is also comfortably over the 48 dp touch-target floor.
 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ControlHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ControlHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ControlHeight),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            contentColor = MaterialTheme.colorScheme.error,
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.errorContainer),
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) androidx.compose.material3.Icon(icon, contentDescription = null, Modifier.heightIn(min = 20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
