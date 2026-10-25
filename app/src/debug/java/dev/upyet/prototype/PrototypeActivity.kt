package dev.upyet.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.upyet.core.ui.theme.UpYetTheme

/**
 * The prototype launcher. A second launcher entry beside the real app, debug-only by construction:
 * the whole source set is `src/debug`, so a release build has no such activity, no such manifest
 * entry and none of the fake screens it reaches.
 */
class PrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { UpYetTheme { PrototypeCatalog() } }
    }
}

/**
 * Home list -> one prototype, and back out again. Back returns here whenever the open prototype does
 * not consume the event itself, which is how a shell variant that treats back as "go to Alarms" still
 * gets you out eventually.
 */
@Composable
private fun PrototypeCatalog() {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = prototypeEntries.firstOrNull { it.id == openId }
    if (open == null) {
        PrototypeHome(onOpen = { openId = it.id })
    } else {
        BackHandler { openId = null }
        open.content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrototypeHome(onOpen: (PrototypeEntry) -> Unit) {
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Prototypes") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            prototypeEntries.forEach { entry ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(entry) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = entry.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = entry.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = "Throwaway, debug-only. Nothing on this screen exists in a release build.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A prototype under review: the issue number, what question it answers, and the screen itself. */
private class PrototypeEntry(val id: String, val title: String, val summary: String, val content: @Composable () -> Unit)

private val prototypeEntries =
    listOf(
        PrototypeEntry(
            id = "12",
            title = "12 · Navigation shell",
            summary = "Three structurally different app shells",
            content = { NavigationShellPrototype() },
        ),
        PrototypeEntry(
            id = "13-timer",
            title = "13 · Timer",
            summary = "Three structurally different timer screens",
            content = { TimerPrototype() },
        ),
        PrototypeEntry(
            id = "13-stopwatch",
            title = "13 · Stopwatch",
            summary = "Three structurally different stopwatch screens",
            content = { StopwatchPrototype() },
        ),
        PrototypeEntry(
            id = "14-timer-ringing",
            title = "14 · Timer ringing",
            summary = "Three structurally different ringing timer screens",
            content = { TimerRingingPrototype() },
        ),
        PrototypeEntry(
            id = "14-timer-heads-up",
            title = "14 · Timer heads-up",
            summary = "Two heads-up notifications while the phone is in use",
            content = { TimerHeadsUpPrototype() },
        ),
        PrototypeEntry(
            id = "15-world-clock",
            title = "15 · World clock",
            summary = "Three structurally different world clock screens",
            content = { WorldClockPrototype() },
        ),
    )
