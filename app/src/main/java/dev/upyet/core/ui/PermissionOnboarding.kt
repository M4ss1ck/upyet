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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import dev.upyet.R
import dev.upyet.core.ui.components.UpYetCard
import dev.upyet.core.ui.theme.ControlHeight
import dev.upyet.core.ui.theme.MinTouchTarget

/**
 * Asks for the two permissions the product actually needs, in context and with an explanation.
 * Camera permission is requested here - never for the first time while an alarm is already ringing.
 *
 * This card is also UpYet's Play **prominent disclosure** for the camera, which is a policy requirement
 * and not decoration: it must stay in-app, stay ahead of the camera permission request, keep saying what
 * is recorded, when it is recorded and that nothing leaves the device, and keep a distinct affirmative
 * tap that is not the system permission dialog. Its copy is quoted in `docs/STORE.md`, where a reviewer
 * reads it. Before changing it, read `docs/adr/0006`.
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
    PermissionDisclosure(onAgree = { launcher.launch(missing.toTypedArray()) }, modifier = modifier)
}

/**
 * The disclosure itself, stateless: what is recorded, when, that nothing leaves the device, and the one
 * affirmative tap that comes before the system dialog. [PermissionOnboardingCard] and [CameraGateHost]
 * both show this exact composable, so the two entry points can never drift apart (ADR 0006).
 * [onDismiss], when given, adds a "Not now" button; the onboarding card passes none.
 */
@Composable
fun PermissionDisclosure(onAgree: () -> Unit, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
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
            onClick = onAgree,
            modifier = Modifier.fillMaxWidth().heightIn(min = ControlHeight).padding(top = 12.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(stringResource(R.string.permission_onboarding), style = MaterialTheme.typography.labelLarge)
        }
        if (onDismiss != null) {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget)) {
                Text(stringResource(R.string.camera_disclosure_dismiss), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Puts the disclosure in front of anything that turns camera evidence on (AGENTS.md §10, ADR 0006
 * amendment). The gate is the missing permission, not a stored consent flag, so it behaves like the
 * onboarding card. Host it with [CameraGateHost] once per screen.
 */
@Stable
class CameraGate internal constructor(internal val isGranted: () -> Boolean) {
    internal var pending: (() -> Unit)? by mutableStateOf(null)

    /** Runs [action] at once when CAMERA is granted; otherwise the disclosure comes first, and [action] runs only once granted. */
    fun whenGranted(action: () -> Unit) {
        if (isGranted()) action() else pending = action
    }
}

/** [isGranted] is a test seam; null checks the real CAMERA permission. */
@Composable
fun rememberCameraGate(isGranted: (() -> Boolean)? = null): CameraGate {
    val context = LocalContext.current
    val check = isGranted ?: { cameraGranted(context) }
    return remember(context, isGranted) { CameraGate(check) }
}

/** Shows the disclosure while an action waits on the gate; the system dialog only ever follows the user's tap on it. */
@Composable
fun CameraGateHost(gate: CameraGate) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val action = gate.pending
            gate.pending = null
            if (action != null && gate.isGranted()) action()
        }
    if (gate.pending == null) return
    Dialog(onDismissRequest = { gate.pending = null }) {
        PermissionDisclosure(
            onAgree = { launcher.launch(missingPermissions(context).toTypedArray()) },
            onDismiss = { gate.pending = null },
        )
    }
}

private fun cameraGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun missingPermissions(context: Context): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    add(Manifest.permission.CAMERA)
}.filter { permission ->
    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
}
