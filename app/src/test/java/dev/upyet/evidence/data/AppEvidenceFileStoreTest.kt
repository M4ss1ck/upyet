package dev.upyet.evidence.data

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppEvidenceFileStoreTest {
    private val store = AppEvidenceFileStore(ApplicationProvider.getApplicationContext())

    @Test fun theThumbnailIsAJpegSiblingOfTheClip() {
        val clip = store.newEvidenceFile()
        val thumbnail = store.resolveThumbnail(clip.name)
        assertThat(thumbnail.parentFile).isEqualTo(clip.parentFile)
        assertThat(thumbnail.name).isEqualTo("${clip.nameWithoutExtension}.jpg")
    }

    @Test fun deletingAClipAlsoDeletesItsThumbnail() {
        val clip = store.newEvidenceFile().also { it.writeBytes(byteArrayOf(1)) }
        val thumbnail = store.resolveThumbnail(clip.name).also { it.writeBytes(byteArrayOf(2)) }
        assertThat(store.delete(clip.name)).isTrue()
        assertThat(clip.exists()).isFalse()
        assertThat(thumbnail.exists()).isFalse()
    }

    @Test fun deletingAClipThatNeverProducedAThumbnailStillReportsTheClip() {
        val clip = store.newEvidenceFile().also { it.writeBytes(byteArrayOf(1)) }
        assertThat(store.delete(clip.name)).isTrue()
        assertThat(clip.exists()).isFalse()
    }
}
