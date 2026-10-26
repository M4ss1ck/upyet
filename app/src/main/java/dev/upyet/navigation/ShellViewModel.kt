package dev.upyet.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.reliability.domain.ReliabilityChecks
import dev.upyet.reliability.domain.ReliabilitySummary
import dev.upyet.reliability.domain.summarize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Feeds the reliability dot on the shell's menu button. The user fixes a permission in system settings and
 * comes back to the app, so the shell re-evaluates on every resume ([refresh]); otherwise the dot would keep
 * claiming a problem that is already fixed, and it must agree with the summary the Settings card shows.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(private val checks: ReliabilityChecks) : ViewModel() {
    private val _reliability = MutableStateFlow(checks.evaluate().summarize())

    /** The current reliability summary. `allGood == false` means the menu button shows the reliability dot. */
    val reliability: StateFlow<ReliabilitySummary> = _reliability.asStateFlow()

    /** Re-runs every check. Called from the shell on each ON_RESUME, never from a background timer. */
    fun refresh() {
        _reliability.value = checks.evaluate().summarize()
    }
}
