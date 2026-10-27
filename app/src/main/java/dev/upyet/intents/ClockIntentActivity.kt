package dev.upyet.intents

import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.upyet.MainActivity
import dev.upyet.navigation.LaunchDestination
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * The only entry point for the platform `AlarmClock` intents (ADR 0007). It has no content: it parses, lets
 * [ClockIntentViewModel] act, applies the resulting [IntentEffect]s from a visible activity and finishes.
 *
 * Every follow-up start is made from this visible activity: the app screens through a normal `startActivity()`, the
 * ringing screens through [RingingSurfaces]. It is not `directBootAware` and never dismisses the keyguard; it shows
 * when locked only so a ringing screen can be brought forward over it.
 */
@AndroidEntryPoint
class ClockIntentActivity : ComponentActivity() {
    private val viewModel: ClockIntentViewModel by viewModels()

    @Inject lateinit var ringingSurfaces: RingingSurfaces

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.handleOnce(ClockIntentParser.parse(intent.action, intent.dataString, BundleIntentExtras(intent.extras)))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val effects = viewModel.effects.filterNotNull().first()
                effects.forEach(::apply)
                finish()
            }
        }
    }

    private fun apply(effect: IntentEffect) {
        when (effect) {
            is IntentEffect.Toast -> toast(effect)
            IntentEffect.OpenAlarms -> open(LaunchDestination.Alarms)
            IntentEffect.OpenTimers -> open(LaunchDestination.Timers)
            IntentEffect.OpenReliability -> open(LaunchDestination.Reliability)
            is IntentEffect.OpenAlarmEditor -> open(LaunchDestination.AlarmEditor(effect.prefill?.let(AlarmPrefillCodec::encode)))
            IntentEffect.BringAlarmRingingForward -> ringingSurfaces.bringAlarmForward()
            IntentEffect.BringTimerRingingForward -> ringingSurfaces.bringTimerForward()
        }
    }

    private fun open(destination: LaunchDestination) = startActivity(MainActivity.destinationIntent(this, destination))

    private fun toast(effect: IntentEffect.Toast) {
        val time = effect.time
        val text = if (time == null) getString(effect.message) else getString(effect.message, formatTime(time))
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    /** The same clock style the editor's time picker follows: the device's 12 or 24 hour setting, in the locale. */
    private fun formatTime(time: LocalTime): String {
        val locale = resources.configuration.locales[0]
        val skeleton = if (DateFormat.is24HourFormat(this)) "Hm" else "hm"
        return time.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
    }
}
