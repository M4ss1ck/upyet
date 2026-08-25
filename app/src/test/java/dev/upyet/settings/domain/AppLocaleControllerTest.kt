package dev.upyet.settings.domain

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.upyet.settings.data.AppLanguage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AppLocaleControllerTest {
    private fun controller() = AppLocaleController(ApplicationProvider.getApplicationContext())

    @Test
    @Config(sdk = [33])
    fun overrideIsSupportedFromTiramisu() {
        assertThat(controller().isSupported).isTrue()
    }

    @Test
    @Config(sdk = [33])
    fun aChosenLanguageIsReadBack() {
        val controller = controller()
        controller.set(AppLanguage.SPANISH)
        assertThat(controller.current()).isEqualTo(AppLanguage.SPANISH)
    }

    @Test
    @Config(sdk = [33])
    fun choosingSystemClearsTheOverride() {
        val controller = controller()
        controller.set(AppLanguage.SPANISH)
        controller.set(AppLanguage.SYSTEM)
        assertThat(controller.current()).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test
    @Config(sdk = [32])
    fun beforeTiramisuThereIsNoOverrideToRead() {
        val controller = controller()
        assertThat(controller.isSupported).isFalse()
        assertThat(controller.current()).isEqualTo(AppLanguage.SYSTEM)
    }
}
