package dev.myalarm.core.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.myalarm.R

@Composable
fun PermissionOnboarding(onComplete: () -> Unit) {
    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        add(Manifest.permission.CAMERA)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onComplete() }
    Button(onClick = { launcher.launch(permissions.toTypedArray()) }) { Text(stringResource(R.string.permission_onboarding)) }
}
