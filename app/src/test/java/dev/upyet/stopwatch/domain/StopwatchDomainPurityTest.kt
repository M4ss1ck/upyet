package dev.upyet.stopwatch.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/** The stopwatch domain is plain Kotlin, so it is tested on the JVM without Robolectric (AGENTS.md §17). */
class StopwatchDomainPurityTest {
    // Gradle runs unit tests with the module directory as the working directory.
    private val domainSources = File("src/main/java/dev/upyet/stopwatch/domain")

    @Test
    fun stopwatchDomainImportsNothingFromAndroid() {
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
