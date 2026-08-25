package dev.upyet.reliability.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.upyet.R
import dev.upyet.core.ui.components.PrimaryButton
import dev.upyet.core.ui.components.RowDivider
import dev.upyet.core.ui.components.SectionLabel
import dev.upyet.core.ui.components.UpYetCard
import dev.upyet.core.ui.components.UpYetTopBar
import dev.upyet.core.ui.currentLocale
import dev.upyet.core.ui.theme.Ink
import dev.upyet.core.ui.theme.RingingPalette
import dev.upyet.core.ui.theme.extraColors
import dev.upyet.reliability.domain.ReliabilityCheck
import dev.upyet.reliability.domain.ReliabilityStatus
import dev.upyet.reliability.domain.summarize
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ReliabilityScreen(onBack: () -> Unit, viewModel: ReliabilityViewModel = hiltViewModel()) {
    val checks by viewModel.state.collectAsStateWithLifecycle()
    ReliabilityContent(checks, onBack)
}

@Composable
fun ReliabilityContent(checks: List<ReliabilityCheck>, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { UpYetTopBar(title = stringResource(R.string.reliability), onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { SummaryHero(checks) }
            item { SectionLabel(stringResource(R.string.reliability_checks), Modifier.padding(top = 20.dp, bottom = 10.dp)) }
            item {
                UpYetCard {
                    checks.forEachIndexed { index, check ->
                        ReliabilityRow(check) { check.settingsIntent?.let(context::startActivity) }
                        if (index != checks.lastIndex) RowDivider()
                    }
                }
            }
        }
    }
}

/** Dark hero: how many checks are blocked, what that does (and does not) mean, and a bar per check. */
@Composable
private fun SummaryHero(checks: List<ReliabilityCheck>, modifier: Modifier = Modifier) {
    val summary = checks.summarize()
    Surface(modifier.fillMaxWidth().padding(top = 14.dp), shape = MaterialTheme.shapes.extraLarge, color = Ink) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                style = MaterialTheme.typography.titleLarge,
                color = RingingPalette.onBackground,
            )
            Text(
                text = stringResource(if (summary.allGood) R.string.reliability_all_good_body else R.string.reliability_summary_body),
                style = MaterialTheme.typography.bodyMedium,
                color = RingingPalette.onBackgroundMuted,
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                checks.forEach { check -> StatusSegment(check.status, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatusSegment(status: ReliabilityStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        ReliabilityStatus.OK -> MaterialTheme.colorScheme.tertiary
        ReliabilityStatus.WARNING -> MaterialTheme.extraColors.warning
        ReliabilityStatus.BLOCKED -> MaterialTheme.colorScheme.error
    }
    Box(modifier.height(6.dp).clip(CircleShape).background(color))
}

@Composable
private fun ReliabilityRow(check: ReliabilityCheck, openSettings: () -> Unit) {
    val style = statusStyle(check.status)
    // Blocked checks are highlighted rather than reordered, so the list still reads top to bottom
    // in the order the checks actually run.
    val rowBackground = if (check.status == ReliabilityStatus.BLOCKED) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }
    Row(
        Modifier.fillMaxWidth().background(rowBackground).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Surface(shape = RoundedCornerShape(11.dp), color = style.container, modifier = Modifier.size(34.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = stringResource(statusTextRes(check.status)),
                    tint = style.content,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(check.titleRes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            val explanation = check.value?.let {
                DateTimeFormatter.ofLocalizedDateTime(
                    FormatStyle.MEDIUM,
                ).withLocale(currentLocale()).withZone(ZoneId.systemDefault()).format(it)
            }
            Text(
                text = when {
                    check.id == "next_alarm" && explanation == null -> stringResource(R.string.reliability_no_next_alarm)
                    explanation == null -> stringResource(check.explanationRes)
                    else -> stringResource(check.explanationRes, explanation)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (check.settingsIntent != null) {
                PrimaryButton(
                    text = stringResource(R.string.open_app_settings),
                    onClick = openSettings,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private data class StatusStyle(val container: Color, val content: Color, val icon: ImageVector)

@Composable
private fun statusStyle(status: ReliabilityStatus): StatusStyle = when (status) {
    ReliabilityStatus.OK -> StatusStyle(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
        Icons.Filled.Check,
    )

    ReliabilityStatus.WARNING -> StatusStyle(
        MaterialTheme.extraColors.warningContainer,
        MaterialTheme.extraColors.onWarningContainer,
        Icons.Filled.Warning,
    )

    ReliabilityStatus.BLOCKED -> StatusStyle(
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.error,
        Icons.Filled.Error,
    )
}

private fun statusTextRes(status: ReliabilityStatus): Int = when (status) {
    ReliabilityStatus.OK -> R.string.status_ok
    ReliabilityStatus.WARNING -> R.string.status_warning
    ReliabilityStatus.BLOCKED -> R.string.status_blocked
}
