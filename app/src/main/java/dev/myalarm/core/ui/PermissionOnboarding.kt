package dev.myalarm.core.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.myalarm.R

private val MIN_TOUCH_TARGET = 48.dp

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
    Card(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null)
                Text(
                    text = stringResource(R.string.permission_onboarding_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.permission_onboarding_explanation),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(
                onClick = { launcher.launch(missing.toTypedArray()) },
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
            ) {
                Text(stringResource(R.string.permission_onboarding))
            }
        }
    }
}

private fun missingPermissions(context: Context): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    add(Manifest.permission.CAMERA)
}.filter { permission ->
    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
}
