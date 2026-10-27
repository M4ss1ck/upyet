package dev.upyet.worldclock.data

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The world map outline from `res/raw/world_land.bin` (ADR 0011). Each ring is a [ShortArray] of
 * interleaved longitude, latitude pairs in hundredths of a degree. Holes are rings of their own, so the
 * map is drawn with an even-odd fill; rings are not clipped.
 */
class WorldLand(val rings: List<ShortArray>) {
    val pointCount: Int get() = rings.sumOf { it.size / 2 }
}

/** Decoder for the `UPLD` layout. Never throws: a malformed asset decodes to null. */
object WorldLandDecoder {
    private val MAGIC = byteArrayOf('U'.code.toByte(), 'P'.code.toByte(), 'L'.code.toByte(), 'D'.code.toByte())

    /** Two int16 per point. */
    private const val BYTES_PER_POINT = 4

    fun decode(bytes: ByteArray): WorldLand? {
        if (bytes.size < MAGIC.size || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        val buffer = ByteBuffer.wrap(bytes, MAGIC.size, bytes.size - MAGIC.size).order(ByteOrder.LITTLE_ENDIAN)
        return try {
            var ringsLeft = buffer.int.toUInt().toLong()
            val rings = ArrayList<ShortArray>()
            // Each ring consumes at least its 4-byte count, so a corrupt ring count ends in underflow, not a hang.
            while (ringsLeft-- > 0) {
                val pointCount = buffer.int.toUInt().toLong()
                // Checked before allocating, so a corrupt count cannot ask for gigabytes.
                if (pointCount * BYTES_PER_POINT > buffer.remaining()) return null
                val points = ShortArray((pointCount * 2).toInt())
                buffer.asShortBuffer().get(points)
                buffer.position(buffer.position() + points.size * Short.SIZE_BYTES)
                rings += points
            }
            // Trailing bytes mean the counts and the payload disagree: not this format.
            if (buffer.hasRemaining()) null else WorldLand(rings)
        } catch (e: BufferUnderflowException) {
            null
        }
    }
}
