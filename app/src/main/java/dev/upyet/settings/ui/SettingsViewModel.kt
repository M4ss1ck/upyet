package dev.upyet.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.reliability.domain.ReliabilityChecks
import dev.upyet.reliability.domain.ReliabilitySummary
import dev.upyet.reliability.domain.summarize
import dev.upyet.settings.data.AppSettings
import dev.upyet.settings.data.RetentionPolicy
import dev.upyet.settings.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Stored preferences plus a one-shot summary of the same checks the reliability screen shows. */
data class SettingsUiState(val settings: AppSettings?, val reliabilitySummary: ReliabilitySummary)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository, reliabilityChecks: ReliabilityChecks) :
    ViewModel() {
    // Evaluated once: this screen only needs a summary to point at the reliability screen, which
    // does its own fresh evaluation, so re-checking permissions on every settings emission is unnecessary.
    private val reliabilitySummary = reliabilityChecks.evaluate().summarize()
    val state = repository.settings
        .map { SettingsUiState(it, reliabilitySummary) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(null, reliabilitySummary))

    fun setRetention(value: RetentionPolicy) = viewModelScope.launch { repository.setRetention(value) }
    fun setSnooze(value: Int) = viewModelScope.launch { repository.setDefaultSnoozeMinutes(value) }
    fun setVibration(value: Boolean) = viewModelScope.launch { repository.setDefaultVibrationEnabled(value) }
    fun setEvidence(value: Boolean) = viewModelScope.launch { repository.setEvidenceEnabledByDefault(value) }
}
