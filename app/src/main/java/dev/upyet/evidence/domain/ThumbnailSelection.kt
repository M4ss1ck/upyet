package dev.upyet.evidence.domain

/**
 * How far back from the reported end the frame is taken. Two reasons it is not the exact end: no frame
 * carries the duration itself as its timestamp, and the duration comes from the recorder's own stats
 * rather than the muxed container, so a clip that was cut short can end before its reported duration does.
 */
private const val END_MARGIN_MS = 100L
private const val MICROS_PER_MILLISECOND = 1_000L

/** The clip a row's thumbnail comes from: the first segment that actually produced a file. */
fun thumbnailSourceFileName(segments: List<EvidenceSegment>): String? = segments.firstNotNullOfOrNull { it.fileName }

/**
 * Microsecond position of the frame to extract. A front camera at wake-up is often still exposing at t=0,
 * so the frame is taken from the end of the clip instead, backed off by [END_MARGIN_MS] to stay inside
 * data the file actually holds. Unknown, invalid, or very short durations retain the position-zero fallback.
 */
fun thumbnailFrameOffsetMicros(durationMs: Long?): Long = durationMs
    ?.takeIf { it > 0L }
    ?.let { (it - END_MARGIN_MS).coerceAtLeast(0L) * MICROS_PER_MILLISECOND }
    ?: 0L
