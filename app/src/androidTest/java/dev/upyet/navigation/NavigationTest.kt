package dev.upyet.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    /**
     * Driven entirely on the main thread. Setting `graph` makes the controller register a lifecycle
     * observer, and `LifecycleRegistry` rejects that from anywhere else, so running the body on the
     * instrumentation thread failed with "addObserver must be called on the main thread" before it could
     * assert anything. A NavController is main-thread-only in production too; the test now uses it the way
     * the app does rather than the platform's rule being a surprise.
     */
    @Test fun destinationsAreReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val controller = NavHostController(instrumentation.targetContext)
            controller.navigatorProvider.addNavigator(ComposeNavigator())
            controller.graph = controller.createGraph(startDestination = "alarms") {
                composable("alarms") {}
                composable("history") {}
                composable("settings") {}
            }
            assertThat(controller.currentDestination?.route).isEqualTo("alarms")
            controller.navigate("history")
            assertThat(controller.currentDestination?.route).isEqualTo("history")
            controller.navigate("settings")
            assertThat(controller.currentDestination?.route).isEqualTo("settings")
        }
    }
}
