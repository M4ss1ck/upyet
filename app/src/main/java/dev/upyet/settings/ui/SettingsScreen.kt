package dev.upyet.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.upyet.BuildConfig
import dev.upyet.R
import dev.upyet.core.ui.components.BrandMark
import dev.upyet.core.ui.components.PrimaryButton
import dev.upyet.core.ui.components.RowDivider
import dev.upyet.core.ui.components.SectionLabel
import dev.upyet.core.ui.components.SettingsRow
import dev.upyet.core.ui.components.UpYetCard
import dev.upyet.reliability.domain.ReliabilitySummary
import dev.upyet.settings.data.RetentionPolicy

@Composable
fun SettingsScreen(onReliability: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onReliability = onReliability,
        onSnooze = viewModel::setSnooze,
        onVibration = viewModel::setVibration,
        onEvidence = viewModel::setEvidence,
        onRetention = viewModel::setRetention,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onReliability: () -> Unit,
    onSnooze: (Int) -> Unit = {},
    onVibration: (Boolean) -> Unit = {},
    onEvidence: (Boolean) -> Unit = {},
    onRetention: (RetentionPolicy) -> Unit = {},
) {
    // Scrollable: this screen grows with every new setting and must never hide one below the fold.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp),
        )

        ReliabilityStatusCard(state.reliabilitySummary, onReliability, Modifier.padding(top = 8.dp))

        SectionLabel(stringResource(R.string.settings_defaults), Modifier.padding(top = 16.dp, bottom = 2.dp))
        UpYetCard {
            var snoozeExpanded by remember { mutableStateOf(false) }
            val snoozeMinutes = state.settings?.defaultSnoozeMinutes ?: DEFAULT_SNOOZE_MINUTES
            Box {
                SettingsRow(
                    title = stringResource(R.string.default_snooze),
                    icon = Icons.Filled.Snooze,
                    value = pluralStringResource(R.plurals.snooze_option, snoozeMinutes, snoozeMinutes),
                    showChevron = true,
                    onClick = { snoozeExpanded = true },
                )
                DropdownMenu(snoozeExpanded, { snoozeExpanded = false }) {
                    SNOOZE_OPTIONS.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text(pluralStringResource(R.plurals.snooze_option, minutes, minutes)) },
                            onClick = {
                                onSnooze(minutes)
                                snoozeExpanded = false
                            },
                        )
                    }
                }
            }
            RowDivider()
            SettingsRow(
                title = stringResource(R.string.default_vibration),
                icon = Icons.Filled.Vibration,
                trailing = { Switch(checked = state.settings?.defaultVibrationEnabled == true, onCheckedChange = onVibration) },
            )
        }

        SectionLabel(stringResource(R.string.settings_evidence), Modifier.padding(top = 16.dp, bottom = 2.dp))
        UpYetCard {
            SettingsRow(
                title = stringResource(R.string.evidence_by_default),
                subtitle = stringResource(R.string.evidence_by_default_summary),
                icon = Icons.Filled.Videocam,
                trailing = { Switch(checked = state.settings?.evidenceEnabledByDefault == true, onCheckedChange = onEvidence) },
            )
            RowDivider()
            var retentionExpanded by remember { mutableStateOf(false) }
            Box {
                SettingsRow(
                    title = stringResource(R.string.evidence_retention),
                    subtitle = stringResource(R.string.evidence_retention_summary),
                    icon = Icons.Filled.AutoDelete,
                    value = stringResource(retentionResource(state.settings?.retention ?: RetentionPolicy.SEVEN_DAYS)),
                    showChevron = true,
                    onClick = { retentionExpanded = true },
                )
                DropdownMenu(retentionExpanded, { retentionExpanded = false }) {
                    RetentionPolicy.entries.forEach { policy ->
                        DropdownMenuItem(
                            text = { Text(stringResource(retentionResource(policy))) },
                            onClick = {
                                onRetention(policy)
                                retentionExpanded = false
                            },
                        )
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.settings_about), Modifier.padding(top = 16.dp, bottom = 2.dp))
        UpYetCard {
            SettingsRow(
                title = stringResource(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                icon = Icons.Filled.Info,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(size = 18.dp)
            Text(
                text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Tinted status card: green when every check passes, error-tinted the moment one is blocked. */
@Composable
private fun ReliabilityStatusCard(summary: ReliabilitySummary, onReliability: () -> Unit, modifier: Modifier = Modifier) {
    val container = if (summary.allGood) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (summary.allGood) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.error
    // The button sits on its own line rather than beside the text: sharing the row left the summary
    // a narrow column that wrapped mid-sentence on any phone-width screen.
    UpYetCard(modifier, contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = container, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (summary.allGood) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        contentDescription = stringResource(if (summary.allGood) R.string.status_ok else R.string.status_blocked),
                        tint = content,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (summary.allGood) {
                        pluralStringResource(R.plurals.reliability_all_good, summary.totalCount, summary.totalCount)
                    } else {
                        pluralStringResource(
                            R.plurals.reliability_needs_attention,
                            summary.blockedCount,
                            summary.blockedCount,
                            summary.totalCount,
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = stringResource(if (summary.allGood) R.string.reliability_all_good_body else R.string.reliability_summary_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.open_reliability),
            onClick = onReliability,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

private const val DEFAULT_SNOOZE_MINUTES = 9
private val SNOOZE_OPTIONS = listOf(1, 5, 9, 10, 15, 20, 30)

private fun retentionResource(policy: RetentionPolicy): Int = when (policy) {
    RetentionPolicy.ONE_DAY -> R.string.retention_one_day
    RetentionPolicy.SEVEN_DAYS -> R.string.retention_seven_days
    RetentionPolicy.THIRTY_DAYS -> R.string.retention_thirty_days
    RetentionPolicy.FOREVER -> R.string.retention_forever
}
