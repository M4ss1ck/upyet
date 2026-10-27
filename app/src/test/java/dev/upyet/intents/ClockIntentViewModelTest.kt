package dev.upyet.intents

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClockIntentViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun effectsStartNullThenCarryTheHandlerResult() = runTest {
        val viewModel = ClockIntentViewModel({ listOf(IntentEffect.OpenAlarms) }, SavedStateHandle())
        assertThat(viewModel.effects.value).isNull()

        viewModel.handleOnce(ClockIntent.ShowAlarms)

        assertThat(viewModel.effects.value).containsExactly(IntentEffect.OpenAlarms)
    }

    @Test fun handlingTwiceOnOneInstanceRunsTheHandlerOnce() = runTest {
        var calls = 0
        val viewModel = ClockIntentViewModel({ calls++.let { emptyList() } }, SavedStateHandle())

        viewModel.handleOnce(ClockIntent.ShowAlarms)
        viewModel.handleOnce(ClockIntent.ShowAlarms)

        assertThat(calls).isEqualTo(1)
    }

    @Test fun aRecreatedViewModelWithTheSameSavedStateDoesNotHandleAgain() = runTest {
        var calls = 0
        val saved = SavedStateHandle()
        ClockIntentViewModel({ calls++.let { emptyList() } }, saved).handleOnce(ClockIntent.ShowAlarms)

        val recreated = ClockIntentViewModel({ calls++.let { emptyList() } }, saved)
        recreated.handleOnce(ClockIntent.ShowAlarms)

        assertThat(calls).isEqualTo(1)
        // Finishes with nothing rather than waiting forever as an invisible window.
        assertThat(recreated.effects.value).isEmpty()
    }

    @Test fun aThrowingHandlerStillEndsWithEffectsSoTheTrampolineFinishes() = runTest {
        val viewModel = ClockIntentViewModel({ error("boom") }, SavedStateHandle())

        viewModel.handleOnce(ClockIntent.ShowAlarms)

        assertThat(viewModel.effects.value).isEmpty()
    }
}
