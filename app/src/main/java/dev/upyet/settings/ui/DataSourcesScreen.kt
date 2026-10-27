package dev.upyet.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import dev.upyet.R
import dev.upyet.core.ui.components.UpYetCard
import dev.upyet.core.ui.components.UpYetTopBar

/**
 * About > Data sources. Static: the attributions are fixed text that the licences require, so there is nothing to
 * load or observe. The app has no INTERNET permission; the links only open in the user's browser through the
 * platform's link handling, which is why this screen needs no network access of its own.
 */
@Composable
fun DataSourcesScreen(onBack: () -> Unit) {
    Scaffold(topBar = { UpYetTopBar(title = stringResource(R.string.settings_data_sources), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.data_sources_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            DataSourceCard(
                name = stringResource(R.string.data_sources_geonames_name),
                supplies = stringResource(R.string.data_sources_geonames_supplies),
                licence = stringResource(R.string.data_sources_geonames_licence),
                changes = stringResource(R.string.data_sources_geonames_changes),
                links = listOf(stringResource(R.string.data_sources_geonames_url), stringResource(R.string.data_sources_cc_by_url)),
            )
            DataSourceCard(
                name = stringResource(R.string.data_sources_natural_earth_name),
                supplies = stringResource(R.string.data_sources_natural_earth_supplies),
                licence = stringResource(R.string.data_sources_natural_earth_licence),
                links = listOf(stringResource(R.string.data_sources_natural_earth_url)),
            )
            DataSourceCard(
                name = stringResource(R.string.data_sources_iana_name),
                supplies = stringResource(R.string.data_sources_iana_supplies),
                licence = stringResource(R.string.data_sources_iana_licence),
                links = listOf(stringResource(R.string.data_sources_iana_url)),
            )
            // The one-line credit the world clock also shows; kept here so the full list has the same wording.
            Text(
                text = stringResource(R.string.world_clock_attribution),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DataSourceCard(name: String, supplies: String, licence: String, links: List<String>, changes: String? = null) {
    UpYetCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(supplies, style = MaterialTheme.typography.bodyMedium)
            Text(licence, style = MaterialTheme.typography.bodySmall)
            if (changes != null) Text(changes, style = MaterialTheme.typography.bodySmall)
            links.forEach { url -> Text(linkText(url), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Underlined so the link reads as tappable; the URL itself is the visible text, so the target is never hidden. */
private fun linkText(url: String) = buildAnnotatedString {
    withLink(
        LinkAnnotation.Url(
            url,
            styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)),
        ),
    ) { append(url) }
}
