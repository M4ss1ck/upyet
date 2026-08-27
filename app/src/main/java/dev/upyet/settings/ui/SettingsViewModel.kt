package dev.upyet.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.alarm.scheduling.AlarmRescheduler
import dev.upyet.reliability.domain.ReliabilityChecks
import dev.upyet.reliability.domain.ReliabilitySummary
import dev.upyet.reliability.domain.summarize
import dev.upyet.settings.data.AppLanguage
import dev.upyet.settings.data.AppSettings
import dev.upyet.settings.data.RetentionPolicy
import dev.upyet.settings.data.SettingsRepository
import dev.upyet.settings.domain.AppLocaleController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Stored preferences plus a one-shot summary of the same checks the reliability screen shows. */
data class SettingsUiState(
    val settings: AppSettings?,
    val reliabilitySummary: ReliabilitySummary,
    val language: AppLanguage,
    val languageSupported: Boolean,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val localeController: AppLocaleController,
    reliabilityChecks: ReliabilityChecks,
    private val rescheduler: AlarmRescheduler,
) : ViewModel() {
    // Evaluated once: this screen only needs a summary to point at the reliability screen, which
    // does its own fresh evaluation, so re-checking permissions on every settings emission is unnecessary.
    private val reliabilitySummary = reliabilityChecks.evaluate().summarize()

    // The platform, not DataStore, stores the language, so it is held here as its own flow rather than
    // arriving with the preferences.
    private val language = MutableStateFlow(localeController.current())

    val state = combine(repository.settings, language) { settings, language ->
        SettingsUiState(settings, reliabilitySummary, language, localeController.isSupported)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(null, reliabilitySummary, language.value, localeController.isSupported),
    )

    /** The language can also be changed from the system's own per-app language screen. */
    fun refreshLanguage() {
        language.value = localeController.current()
    }

    fun setLanguage(value: AppLanguage) {
        localeController.set(value)
        language.value = value
    }

    fun setRetention(value: RetentionPolicy) = viewModelScope.launch { repository.setRetention(value) }
    fun setSnooze(value: Int) = viewModelScope.launch { repository.setDefaultSnoozeMinutes(value) }
    fun setMaxSnoozes(value: Int) = viewModelScope.launch { repository.setMaxSnoozes(value) }
    fun setVibration(value: Boolean) = viewModelScope.launch { repository.setDefaultVibrationEnabled(value) }
    fun setEvidence(value: Boolean) = viewModelScope.launch { repository.setEvidenceEnabledByDefault(value) }

    fun setUpcomingAlarmLead(value: Int) = viewModelScope.launch {
        repository.setUpcomingAlarmLeadMinutes(value)
        rescheduler.refreshUpcoming()
    }
}
