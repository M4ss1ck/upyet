package dev.upyet.core.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiagnosticLogStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun appendedLinesAreReadBackInOrder() {
        val store = DiagnosticLogStore(temporaryFolder.newFolder())
        store.append("alpha")
        store.append("beta")
        store.append("gamma")

        assertThat(store.read()).isEqualTo("alpha\nbeta\ngamma\n")
    }

    @Test
    fun exceedingMaxBytesRotatesAndKeepsBothContents() {
        val dir = temporaryFolder.newFolder()
        val store = DiagnosticLogStore(dir)
        val filler = "a".repeat(DiagnosticLogStore.MAX_BYTES)
        store.append(filler)
        val before = store.read()
        store.append("second")

        val after = store.read()
        assertThat(after).contains(filler)
        assertThat(after).contains("second")
        assertThat(after.indexOf(filler)).isLessThan(after.indexOf("second"))
        assertThat(before).isEqualTo("$filler\n")
        assertThat(File(dir, DiagnosticLogStore.PREVIOUS).exists()).isTrue()
        assertThat(File(dir, DiagnosticLogStore.CURRENT).exists()).isTrue()
    }

    @Test
    fun secondRotationDiscardsOldestContentAndKeepsExactlyTwoFiles() {
        val dir = temporaryFolder.newFolder()
        val store = DiagnosticLogStore(dir)
        val fillerOne = "1".repeat(DiagnosticLogStore.MAX_BYTES - 10)
        val fillerTwo = "2".repeat(DiagnosticLogStore.MAX_BYTES - 10)
        val fillerThree = "3".repeat(DiagnosticLogStore.MAX_BYTES - 10)

        store.append(fillerOne)
        store.append(fillerTwo)
        store.append(fillerThree)

        val content = store.read()
        assertThat(content).doesNotContain(fillerOne)
        assertThat(content).contains(fillerTwo)
        assertThat(content).contains(fillerThree)
        assertThat(content.indexOf(fillerTwo)).isLessThan(content.indexOf(fillerThree))
        val files = dir.listFiles()?.map { it.name } ?: emptyList()
        assertThat(files).containsExactly(DiagnosticLogStore.CURRENT, DiagnosticLogStore.PREVIOUS)
    }

    @Test
    fun readOnEmptyOrNonExistentDirectoryReturnsEmpty() {
        val dir = temporaryFolder.newFolder()
        val emptyStore = DiagnosticLogStore(File(dir, "missing"))
        assertThat(emptyStore.read()).isEqualTo("")

        val existingEmptyDir = temporaryFolder.newFolder()
        val store = DiagnosticLogStore(existingEmptyDir)
        assertThat(store.read()).isEqualTo("")
    }

    @Test
    fun appendDoesNotThrowWhenDirectoryIsNotWritable() {
        val fileAsDir = temporaryFolder.newFile()
        val store = DiagnosticLogStore(fileAsDir)

        var threw = false
        try {
            store.append("line")
        } catch (_: Exception) {
            threw = true
        }
        assertThat(threw).isFalse()
    }
}
