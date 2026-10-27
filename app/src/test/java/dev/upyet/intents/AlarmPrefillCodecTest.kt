package dev.upyet.intents

import com.google.common.truth.Truth.assertThat
import dev.upyet.alarm.domain.Recurrence
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Base64

class AlarmPrefillCodecTest {
    private val urlSafe = Regex("[A-Za-z0-9_-]+")
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    private fun prefill(
        label: String = "Gym",
        recurrence: Recurrence = Recurrence.OneTime,
        sound: AlarmSound = AlarmSound.Default,
        vibrate: Boolean? = null,
        time: LocalTime = LocalTime.of(6, 45),
    ) = AlarmPrefill(time = time, recurrence = recurrence, label = label, sound = sound, vibrate = vibrate)

    /** Builds a token from raw payload text, so malformed-but-URL-safe inputs can be tested directly. */
    private fun token(payload: String): String = encoder.encodeToString(payload.toByteArray(Charsets.UTF_8))

    @Test
    fun roundTripsLabelsWithSeparatorsEmojiAndSlashes() {
        val labels = listOf(
            "Gym|run",
            "a|b|c|",
            "⏰ Gym 🏋️",
            "Walk/dog?now=1&x=2#top%20",
            "",
            "  padded  ",
        )
        labels.forEach { label ->
            val p = prefill(label = label)
            assertThat(AlarmPrefillCodec.decode(AlarmPrefillCodec.encode(p))).isEqualTo(p)
        }
    }

    @Test
    fun roundTripsEveryTimeOfDayAtMinutePrecision() {
        listOf(LocalTime.MIDNIGHT, LocalTime.of(0, 1), LocalTime.of(12, 0), LocalTime.of(23, 59)).forEach { time ->
            val p = prefill(time = time)
            assertThat(AlarmPrefillCodec.decode(AlarmPrefillCodec.encode(p))).isEqualTo(p)
        }
    }

    @Test
    fun roundTripsEveryRecurrence() {
        val recurrences = listOf(
            Recurrence.OneTime,
            Recurrence.Daily,
            Recurrence.Weekly(setOf(DayOfWeek.FRIDAY)),
            Recurrence.Weekly(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY)),
            Recurrence.Weekly(DayOfWeek.entries.toSet()),
        )
        recurrences.forEach { recurrence ->
            val p = prefill(recurrence = recurrence)
            assertThat(AlarmPrefillCodec.decode(AlarmPrefillCodec.encode(p))).isEqualTo(p)
        }
    }

    @Test
    fun roundTripsEveryVibrateVariant() {
        listOf(null, true, false).forEach { vibrate ->
            val p = prefill(vibrate = vibrate)
            assertThat(AlarmPrefillCodec.decode(AlarmPrefillCodec.encode(p))).isEqualTo(p)
        }
    }

    @Test
    fun roundTripsEverySoundVariantIncludingUriWithQueryCharacters() {
        val sounds = listOf(
            AlarmSound.Default,
            AlarmSound.Silent,
            AlarmSound.Uri("content://media/external/audio/media/42"),
            AlarmSound.Uri("content://media/x?a=1&b=2&c==3#frag"),
            AlarmSound.Uri("file:///storage/emulated/0/Music/a|b.ogg"),
        )
        sounds.forEach { sound ->
            val p = prefill(sound = sound)
            assertThat(AlarmPrefillCodec.decode(AlarmPrefillCodec.encode(p))).isEqualTo(p)
        }
    }

    @Test
    fun encodedOutputIsUrlSafeTokenOnly() {
        val samples = listOf(
            prefill(),
            prefill(label = "⏰ a/b?c=d&e#f|g", sound = AlarmSound.Uri("content://x?y=z&w=~"), vibrate = true),
            prefill(recurrence = Recurrence.Weekly(DayOfWeek.entries.toSet()), label = "😀"),
        )
        samples.forEach { p ->
            val encoded = AlarmPrefillCodec.encode(p)
            assertThat(urlSafe.matches(encoded)).isTrue()
        }
    }

    @Test
    fun decodeNullAndEmptyAreNull() {
        assertThat(AlarmPrefillCodec.decode(null)).isNull()
        assertThat(AlarmPrefillCodec.decode("")).isNull()
    }

    @Test
    fun decodeGarbageIsNullAndNeverThrows() {
        listOf("!!!", "%%%", "not base64 at all", "abc def", "a", "+/==", "=", "a=b", "éè").forEach { garbage ->
            assertThat(AlarmPrefillCodec.decode(garbage)).isNull()
        }
    }

    @Test
    fun decodeRejectsPaddedToken() {
        val valid = AlarmPrefillCodec.encode(prefill())
        assertThat(AlarmPrefillCodec.decode("$valid=")).isNull()
    }

    @Test
    fun decodeRejectsWrongVersion() {
        assertThat(AlarmPrefillCodec.decode(token("2|06:45|O||D|"))).isNull()
    }

    @Test
    fun decodeRejectsWrongFieldCount() {
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|O|D|"))).isNull()
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|O|||D||"))).isNull()
    }

    @Test
    fun decodeRejectsMalformedTime() {
        assertThat(AlarmPrefillCodec.decode(token("1|24:00|O||D|"))).isNull()
        assertThat(AlarmPrefillCodec.decode(token("1|6:45|O||D|"))).isNull()
        assertThat(AlarmPrefillCodec.decode(token("1|06:60|O||D|"))).isNull()
        assertThat(AlarmPrefillCodec.decode(token("1|06-45|O||D|"))).isNull()
    }

    @Test
    fun decodeRejectsMalformedRecurrence() {
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|X||D|"))).isNull()
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|W||D|"))).isNull()
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|W0||D|"))).isNull()
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|W8||D|"))).isNull()
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|W11||D|"))).isNull()
    }

    @Test
    fun decodeRejectsMalformedVibrate() {
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|O|2|D|"))).isNull()
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|O|true|D|"))).isNull()
    }

    @Test
    fun decodeRejectsMalformedSound() {
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|O||Z|"))).isNull()
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|O||U!!!|"))).isNull()
    }

    @Test
    fun decodeRejectsMalformedLabel() {
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|O||D|!!"))).isNull()
    }

    @Test
    fun decodeRejectsLabelThatIsNotUtf8() {
        val notUtf8 = encoder.encodeToString(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        assertThat(AlarmPrefillCodec.decode(token("1|06:45|O||D|$notUtf8"))).isNull()
    }

    @Test
    fun decodeAcceptsMinimalWellFormedPayload() {
        val decoded = AlarmPrefillCodec.decode(token("1|06:45|O||D|"))
        assertThat(decoded).isEqualTo(prefill(label = "", recurrence = Recurrence.OneTime))
    }
}
