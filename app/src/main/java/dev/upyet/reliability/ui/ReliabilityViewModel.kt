package dev.upyet.reliability.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.reliability.domain.ReliabilityCheck
import dev.upyet.reliability.domain.ReliabilityChecks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ReliabilityViewModel @Inject constructor(private val checks: ReliabilityChecks) : ViewModel() {
    private val _state = MutableStateFlow(checks.evaluate())
    val state = _state.asStateFlow()
    fun refresh() {
        _state.value = checks.evaluate()
    }
}
