package dev.upyet.evidence.data

import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.upyet.evidence.domain.thumbnailFrameOffsetMicros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Roughly a screenful of 62dp rows worth of decoded frames; evicting one only costs a re-decode. */
private const val MAX_CACHE_BYTES = 4 * 1024 * 1024
private const val BYTES_PER_PIXEL = 4

/**
 * Decoded history thumbnails, keyed by the clip's file name.
 *
 * A missing sibling JPEG is the only signal that a clip has no thumbnail yet, which covers both clips
 * recorded before this existed and clips whose extraction at finalization failed; the first render that
 * asks for one backfills it. Nothing here is persisted beyond the JPEG itself.
 */
@Singleton
class EvidenceThumbnailCache @Inject constructor(private val files: EvidenceFileStore, private val extractor: EvidenceThumbnailExtractor) {
    private val decoded = object : LruCache<String, ImageBitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * BYTES_PER_PIXEL
    }

    /**
     * Clips that could not produce a frame. Without this a broken clip would be re-decoded on every
     * recomposition; it is in-memory only, so a restart gives every clip one more chance.
     */
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    suspend fun thumbnail(fileName: String, durationMs: Long?): ImageBitmap? {
        decoded[fileName]?.let { return it }
        if (fileName in failed) return null
        return withContext(Dispatchers.IO) {
            val target = files.resolveThumbnail(fileName)
            if (!target.isFile) {
                val clip = files.resolve(fileName)
                if (!clip.isFile || !extractor.extract(clip, target, thumbnailFrameOffsetMicros(durationMs))) {
                    failed += fileName
                    return@withContext null
                }
            }
            val bitmap = BitmapFactory.decodeFile(target.path)?.asImageBitmap()
            if (bitmap == null) failed += fileName else decoded.put(fileName, bitmap)
            bitmap
        }
    }
}
