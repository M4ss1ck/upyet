package dev.upyet.intents

import dev.upyet.alarm.domain.Recurrence
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Base64
import java.util.Locale

/**
 * Packs an [AlarmPrefill] into one token that travels as a navigation route argument.
 *
 * Why base64url: a route argument sits in a path segment. A label may hold `/`, `?`, `#`, `%` or emoji, and each of
 * those would need escaping in the route. A base64url token needs none. Fields are pipe-separated inside the payload,
 * and free text (label, sound URI) is base64url-encoded itself, so no user text can shift the field positions.
 *
 * Payload fields, in order: version, `HH:mm`, recurrence, vibrate, sound, label.
 */
object AlarmPrefillCodec {
    private const val VERSION = "1"
    private const val SEPARATOR = "|"
    private const val FIELD_COUNT = 6

    private const val ONE_TIME = "O"
    private const val DAILY = "D"
    private const val WEEKLY = 'W'
    private const val VIBRATE_UNSET = ""
    private const val VIBRATE_ON = "1"
    private const val VIBRATE_OFF = "0"
    private const val SOUND_DEFAULT = "D"
    private const val SOUND_SILENT = "S"
    private const val SOUND_URI = 'U'

    private val URL_SAFE = Regex("[A-Za-z0-9_-]*")
    private val TIME = Regex("([01][0-9]|2[0-3]):([0-5][0-9])")
    private val ENCODER = Base64.getUrlEncoder().withoutPadding()
    private val DECODER = Base64.getUrlDecoder()

    fun encode(p: AlarmPrefill): String {
        val fields = listOf(
            VERSION,
            String.format(Locale.ROOT, "%02d:%02d", p.time.hour, p.time.minute),
            recurrenceCode(p.recurrence),
            vibrateCode(p.vibrate),
            soundCode(p.sound),
            base64(p.label),
        )
        return base64(fields.joinToString(SEPARATOR))
    }

    /** Returns null for any input this codec would not produce. Never throws. */
    fun decode(s: String?): AlarmPrefill? {
        if (s == null) return null
        val fields = unBase64(s)?.split(SEPARATOR) ?: return null
        if (fields.size != FIELD_COUNT || fields[0] != VERSION) return null
        val time = parseTime(fields[1]) ?: return null
        val recurrence = parseRecurrence(fields[2]) ?: return null
        val vibrate = when (fields[3]) {
            VIBRATE_UNSET -> null
            VIBRATE_ON -> true
            VIBRATE_OFF -> false
            else -> return null
        }
        val sound = parseSound(fields[4]) ?: return null
        val label = unBase64(fields[5]) ?: return null
        return AlarmPrefill(time = time, recurrence = recurrence, label = label, sound = sound, vibrate = vibrate)
    }

    private fun recurrenceCode(recurrence: Recurrence): String = when (recurrence) {
        Recurrence.OneTime -> ONE_TIME
        Recurrence.Daily -> DAILY
        is Recurrence.Weekly -> WEEKLY + recurrence.days.map { it.value }.sorted().joinToString("")
    }

    private fun vibrateCode(vibrate: Boolean?): String = when (vibrate) {
        null -> VIBRATE_UNSET
        true -> VIBRATE_ON
        false -> VIBRATE_OFF
    }

    private fun soundCode(sound: AlarmSound): String = when (sound) {
        AlarmSound.Default -> SOUND_DEFAULT
        AlarmSound.Silent -> SOUND_SILENT
        is AlarmSound.Uri -> SOUND_URI + base64(sound.value)
    }

    private fun parseTime(text: String): LocalTime? {
        val match = TIME.matchEntire(text) ?: return null
        val (hour, minute) = match.destructured
        return LocalTime.of(hour.toInt(), minute.toInt())
    }

    private fun parseRecurrence(code: String): Recurrence? = when {
        code == ONE_TIME -> Recurrence.OneTime
        code == DAILY -> Recurrence.Daily
        code.startsWith(WEEKLY) -> parseWeekly(code.substring(1))
        else -> null
    }

    /** Digits 1-7 (DayOfWeek values), each at most once. A repeated digit is malformed, not merged. */
    private fun parseWeekly(digits: String): Recurrence? {
        val valid = digits.isNotEmpty() && digits.all { it in '1'..'7' } && digits.toSet().size == digits.length
        if (!valid) return null
        return Recurrence.Weekly(digits.map { DayOfWeek.of(it - '0') }.toSet())
    }

    private fun parseSound(code: String): AlarmSound? = when {
        code == SOUND_DEFAULT -> AlarmSound.Default
        code == SOUND_SILENT -> AlarmSound.Silent
        code.startsWith(SOUND_URI) -> unBase64(code.substring(1))?.let { AlarmSound.Uri(it) }
        else -> null
    }

    private fun base64(text: String): String = ENCODER.encodeToString(text.toByteArray(StandardCharsets.UTF_8))

    /**
     * Strict decode: rejects characters outside the URL-safe alphabet (so `=` padding and `+` or `/` fail), and
     * rejects byte sequences that are not valid UTF-8 instead of substituting U+FFFD.
     */
    private fun unBase64(token: String): String? {
        if (!URL_SAFE.matches(token)) return null
        return try {
            val bytes = DECODER.decode(token)
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (malformed: IllegalArgumentException) {
            null
        } catch (notUtf8: CharacterCodingException) {
            null
        }
    }
}
