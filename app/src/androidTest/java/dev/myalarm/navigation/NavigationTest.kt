package dev.myalarm.navigation

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
    @Test fun destinationsAreReachable() {
        val controller = NavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
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
