package dev.upyet.timer.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/** Issue #33: the timer domain is plain Kotlin, so every later slice can test it on the JVM without Robolectric. */
class TimerDomainPurityTest {
    // Gradle runs unit tests with the module directory as the working directory.
    private val domainSources = File("src/main/java/dev/upyet/timer/domain")

    @Test
    fun timerDomainImportsNothingFromAndroid() {
        val files = domainSources.listFiles { file -> file.extension == "kt" }.orEmpty()
        assertThat(files).isNotEmpty()

        val androidImports = files.flatMap { file ->
            file.readLines()
                .filter { it.startsWith("import android.") || it.startsWith("import androidx.") }
                .map { "${file.name}: $it" }
        }
        assertThat(androidImports).isEmpty()
    }
}
