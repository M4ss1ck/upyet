package dev.upyet.evidence.domain

/** How far into a clip the history thumbnail frame is taken from. */
private const val THUMBNAIL_FRAME_OFFSET_MS = 1_000L
private const val MICROS_PER_MILLISECOND = 1_000L

/** The clip a row's thumbnail comes from: the first segment that actually produced a file. */
fun thumbnailSourceFileName(segments: List<EvidenceSegment>): String? = segments.firstNotNullOfOrNull { it.fileName }

/**
 * Microsecond offset of the frame to extract. A front camera at wake-up is often still exposing at
 * t=0, so prefer ~1s in; clips shorter than that fall back to the first frame.
 */
fun thumbnailFrameOffsetMicros(durationMs: Long?): Long = if (durationMs != null && durationMs > THUMBNAIL_FRAME_OFFSET_MS) {
    THUMBNAIL_FRAME_OFFSET_MS * MICROS_PER_MILLISECOND
} else {
    0L
}
