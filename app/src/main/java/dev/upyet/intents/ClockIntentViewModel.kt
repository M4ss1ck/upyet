package dev.upyet.intents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.upyet.core.logging.AlarmLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_HANDLED = "handled"

/**
 * Runs one platform request exactly once. The handler can create an alarm or a timer, so a rotation or a recreation
 * of the trampoline must never run it again: the guard lives in [SavedStateHandle], which survives process death too.
 * [effects] stays null until the handler has finished, which is the activity's cue to apply them and finish.
 */
@HiltViewModel
class ClockIntentViewModel @Inject constructor(private val handler: ClockIntentHandling, private val savedStateHandle: SavedStateHandle) :
    ViewModel() {
    private val _effects = MutableStateFlow<List<IntentEffect>?>(null)
    val effects: StateFlow<List<IntentEffect>?> = _effects.asStateFlow()

    private var startedHere = false

    fun handleOnce(intent: ClockIntent) {
        if (startedHere) return
        startedHere = true
        if (savedStateHandle.get<Boolean>(KEY_HANDLED) == true) {
            // Handled by a process that died before the activity finished. Running it again could create a second
            // alarm, and emitting nothing would leave the translucent window over the screen, so finish with no effects.
            _effects.value = emptyList()
            return
        }
        savedStateHandle[KEY_HANDLED] = true
        viewModelScope.launch { _effects.value = handleSafely(intent) }
    }

    /** A throw must still end in effects: a trampoline that never finishes is an invisible window eating touches. */
    private suspend fun handleSafely(intent: ClockIntent): List<IntentEffect> = try {
        handler.handle(intent)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AlarmLog.event("clock_intent_failed", "error" to e.javaClass.simpleName)
        emptyList()
    }
}
