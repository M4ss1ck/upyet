package dev.upyet.core.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.upyet.R
import dev.upyet.core.ui.components.UpYetCard
import dev.upyet.core.ui.theme.ControlHeight

/**
 * Asks for the two permissions the product actually needs, in context and with an explanation.
 * Camera permission is requested here - never for the first time while an alarm is already ringing.
 */
@Composable
fun PermissionOnboardingCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(missingPermissions(context)) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            missing = missingPermissions(context)
        }
    if (missing.isEmpty()) return
    UpYetCard(modifier, contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.permission_onboarding_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 11.dp),
            )
        }
        Text(
            text = stringResource(R.string.permission_onboarding_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            onClick = { launcher.launch(missing.toTypedArray()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = ControlHeight).padding(top = 12.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(stringResource(R.string.permission_onboarding), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun missingPermissions(context: Context): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    add(Manifest.permission.CAMERA)
}.filter { permission ->
    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
}
