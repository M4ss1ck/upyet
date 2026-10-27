package dev.upyet.evidence.data

import dev.upyet.evidence.domain.TimerAttribution
import dev.upyet.timer.domain.TimerId

/**
 * Stores a timer occurrence's attribution in one text column as `<timerId>:<labelLength>:<label>` entries, back to back.
 * The label is length-prefixed rather than delimited so that a label may hold any character, including `:`, `|`,
 * a newline or an emoji. Length is counted in `String.length` units (UTF-16), the same units `substring` uses, so an
 * emoji round-trips even though it spans two units.
 *
 * Decoding never throws: a row written by some future or damaged code yields the entries that still parse.
 */
object TimerAttributionCodec {
    fun encode(timers: List<TimerAttribution>): String = buildString {
        timers.forEach { timer ->
            append(timer.timerId.value)
            append(':')
            append(timer.label.length)
            append(':')
            append(timer.label)
        }
    }

    fun decode(value: String?): List<TimerAttribution> {
        if (value.isNullOrEmpty()) return emptyList()
        val decoded = mutableListOf<TimerAttribution>()
        var cursor = 0
        while (cursor < value.length) {
            val idEnd = value.indexOf(':', cursor).takeIf { it >= 0 } ?: break
            val id = parseUnsigned(value.substring(cursor, idEnd))?.takeIf { it > 0 } ?: break
            val lengthEnd = value.indexOf(':', idEnd + 1).takeIf { it >= 0 } ?: break
            val length = parseUnsigned(value.substring(idEnd + 1, lengthEnd))?.takeIf { it <= value.length } ?: break
            val labelStart = lengthEnd + 1
            val labelEnd = labelStart + length.toInt()
            if (labelEnd > value.length) break
            decoded += TimerAttribution(TimerId(id), value.substring(labelStart, labelEnd))
            cursor = labelEnd
        }
        return decoded
    }

    /** Only plain ASCII digits: `toLongOrNull` would also accept a sign, which this format never writes. */
    private fun parseUnsigned(text: String): Long? = text.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }?.toLongOrNull()
}
