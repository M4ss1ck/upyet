package dev.upyet.worldclock.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WorldLandDecoderTest {
    private fun upld(vararg rings: ShortArray, trailing: Int = 0): ByteArray {
        val size = 8 + rings.sumOf { 4 + it.size * 2 } + trailing
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("UPLD".toByteArray(Charsets.US_ASCII)).putInt(rings.size)
        for (ring in rings) {
            buffer.putInt(ring.size / 2)
            ring.forEach { buffer.putShort(it) }
        }
        return buffer.array()
    }

    @Test
    fun decodesRingsInOrderWithSignedLittleEndianPoints() {
        val outer = shortArrayOf(-18000, -9000, 18000, -9000, 0, 9000)
        val hole = shortArrayOf(100, 100, 200, 100, 150, 200)

        val land = WorldLandDecoder.decode(upld(outer, hole))

        assertThat(land).isNotNull()
        assertThat(land!!.rings).hasSize(2)
        assertThat(land.rings[0].toList()).isEqualTo(outer.toList())
        assertThat(land.rings[1].toList()).isEqualTo(hole.toList())
        assertThat(land.pointCount).isEqualTo(6)
    }

    @Test
    fun emptyRingListDecodes() {
        assertThat(WorldLandDecoder.decode(upld())!!.rings).isEmpty()
    }

    @Test
    fun wrongMagicIsNull() {
        val bytes = upld(shortArrayOf(1, 2))
        bytes[0] = 'X'.code.toByte()

        assertThat(WorldLandDecoder.decode(bytes)).isNull()
        assertThat(WorldLandDecoder.decode(byteArrayOf(0x55))).isNull()
    }

    @Test
    fun truncatedPayloadIsNull() {
        val bytes = upld(shortArrayOf(1, 2, 3, 4))

        assertThat(WorldLandDecoder.decode(bytes.copyOf(bytes.size - 1))).isNull()
        assertThat(WorldLandDecoder.decode(bytes.copyOf(6))).isNull()
    }

    @Test
    fun hugePointCountIsNullWithoutAllocating() {
        val bytes = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .put("UPLD".toByteArray(Charsets.US_ASCII)).putInt(1).putInt(-1).array()

        assertThat(WorldLandDecoder.decode(bytes)).isNull()
    }

    @Test
    fun trailingBytesAreNull() {
        assertThat(WorldLandDecoder.decode(upld(shortArrayOf(1, 2), trailing = 2))).isNull()
    }

    @Test
    fun committedAssetDecodes() {
        val land = WorldLandDecoder.decode(File("src/main/res/raw/world_land.bin").readBytes())

        assertThat(land).isNotNull()
        assertThat(land!!.rings).isNotEmpty()
    }
}
