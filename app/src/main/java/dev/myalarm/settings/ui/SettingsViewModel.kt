package dev.myalarm.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.myalarm.settings.data.RetentionPolicy
import dev.myalarm.settings.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setRetention(value: RetentionPolicy) = viewModelScope.launch { repository.setRetention(value) }
    fun setSnooze(value: Int) = viewModelScope.launch { repository.setDefaultSnoozeMinutes(value) }
    fun setVibration(value: Boolean) = viewModelScope.launch { repository.setDefaultVibrationEnabled(value) }
    fun setEvidence(value: Boolean) = viewModelScope.launch { repository.setEvidenceEnabledByDefault(value) }
}
