package dev.upyet.evidence.data

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Short edge of the stored frame. The history row shows it at 62dp, so nothing larger is ever needed. */
private const val THUMBNAIL_SHORT_EDGE_PX = 256
private const val THUMBNAIL_JPEG_QUALITY = 80
private const val QUARTER_TURN_DEGREES = 90
private const val THREE_QUARTER_TURN_DEGREES = 270

interface EvidenceThumbnailExtractor {
    /** Writes a JPEG for [clip] to [target]. Returns false on any decode failure. Never throws. */
    fun extract(clip: File, target: File, offsetMicros: Long): Boolean
}

@Singleton
class MediaMetadataThumbnailExtractor @Inject constructor() : EvidenceThumbnailExtractor {
    override fun extract(clip: File, target: File, offsetMicros: Long): Boolean {
        val retriever = MediaMetadataRetriever()
        // MediaMetadataRetriever only became AutoCloseable at API 29 and minSdk is 26, so the native
        // handle is released by hand instead of with use {}.
        return try {
            retriever.setDataSource(clip.absolutePath)
            val frame = scaledFrame(retriever, offsetMicros) ?: return false
            writeJpeg(frame, target)
        } catch (_: IllegalArgumentException) {
            // setDataSource on a path it cannot open, or a frame request with a size it rejects.
            false
        } catch (_: IllegalStateException) {
            // The retriever refused the request because the data source never came up.
            false
        } catch (_: IOException) {
            // Writing the JPEG failed - a full or unwritable evidence directory.
            false
        } catch (_: RuntimeException) {
            // A corrupt or truncated container surfaces from the native layer as a bare RuntimeException
            // with no documented subtype, so it has to be named; this is deliberately not catch (Exception).
            false
        } finally {
            retriever.release()
        }
    }

    /**
     * Decodes the frame at [offsetMicros] already reduced to thumbnail size. From API 27 the platform
     * scales during decode, which avoids materialising a full 720p bitmap; below that, and whenever the
     * container does not report usable dimensions, the full frame is decoded and scaled afterwards.
     */
    private fun scaledFrame(retriever: MediaMetadataRetriever, offsetMicros: Long): Bitmap? {
        val size = displaySize(retriever)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && size != null) {
            val (width, height) = scaledToShortEdge(size.first, size.second)
            return retriever.getScaledFrameAtTime(offsetMicros, MediaMetadataRetriever.OPTION_CLOSEST, width, height)
        }
        val full = retriever.getFrameAtTime(offsetMicros, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
        val (width, height) = scaledToShortEdge(full.width, full.height)
        return full.scale(width, height)
    }

    /**
     * The frame dimensions as the decoder will hand them back. A portrait front-camera clip is stored
     * landscape plus a quarter-turn rotation, so the raw metadata alone would flip the requested box.
     */
    private fun displaySize(retriever: MediaMetadataRetriever): Pair<Int, Int>? {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
        if (width <= 0 || height <= 0) return null
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        return if (rotation == QUARTER_TURN_DEGREES || rotation == THREE_QUARTER_TURN_DEGREES) height to width else width to height
    }

    /** Scales so the short edge is [THUMBNAIL_SHORT_EDGE_PX], keeping the aspect ratio and never upscaling. */
    private fun scaledToShortEdge(width: Int, height: Int): Pair<Int, Int> {
        val shortEdge = minOf(width, height)
        if (shortEdge <= THUMBNAIL_SHORT_EDGE_PX) return width to height
        val scale = THUMBNAIL_SHORT_EDGE_PX.toDouble() / shortEdge
        return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
    }

    /**
     * Writes through a temporary sibling and renames into place. The reader treats the presence of the
     * JPEG as proof that it is complete, so a half-written file must never be visible under that name.
     */
    private fun writeJpeg(frame: Bitmap, target: File): Boolean {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            val compressed = FileOutputStream(temporary).use {
                frame.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, it)
            }
            return compressed && temporary.renameTo(target)
        } finally {
            // A partial file left in evidence storage would never be read and never be cleaned up;
            // after a successful rename there is nothing left to delete.
            temporary.delete()
        }
    }
}
