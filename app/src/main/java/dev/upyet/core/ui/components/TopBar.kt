package dev.upyet.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.upyet.R

/** Test tag on the reliability dot drawn over the menu button when a blocking check needs attention. */
const val RELIABILITY_DOT_TAG = "reliability_dot"

/** Back-chevron app bar for the screens you push onto the stack. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpYetTopBar(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = {
            if (subtitle == null) {
                Text(title, style = MaterialTheme.typography.titleLarge)
            } else {
                androidx.compose.foundation.layout.Column {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

/**
 * The tab's name at display size, with the menu button beside it - the shell's only navigation affordance
 * apart from the bottom bar. A small dot marks a blocking reliability problem; because a dot alone carries
 * no meaning for a screen reader, the button's own description changes to say so.
 */
@Composable
fun TabHeadline(title: String, actionNeeded: Boolean, onMenu: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            BadgedBox(
                badge = {
                    if (actionNeeded) Badge(Modifier.size(8.dp).testTag(RELIABILITY_DOT_TAG))
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(
                        if (actionNeeded) R.string.open_menu_action_needed else R.string.open_menu,
                    ),
                )
            }
        }
        Text(text = title, style = MaterialTheme.typography.displaySmall)
    }
}
