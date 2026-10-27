package dev.upyet.evidence.data

import com.google.common.truth.Truth.assertThat
import dev.upyet.evidence.domain.TimerAttribution
import dev.upyet.timer.domain.TimerId
import org.junit.Test

class TimerAttributionCodecTest {
    private fun attribution(id: Long, label: String) = TimerAttribution(TimerId(id), label)

    @Test
    fun roundTripsLabelsWithEveryKindOfCharacter() {
        val timers = listOf(
            attribution(1, "Pasta"),
            attribution(2, "tea: 3:05 | later"),
            attribution(3, "12:00:00"),
            attribution(4, "line one\nline two"),
            attribution(5, "🍝 pasta, 🫖"),
            attribution(6, ""),
            attribution(7, "3:abc"),
            attribution(8, "ñandú"),
        )

        assertThat(TimerAttributionCodec.decode(TimerAttributionCodec.encode(timers))).isEqualTo(timers)
    }

    @Test
    fun encodesIdColonLengthColonLabel() {
        assertThat(TimerAttributionCodec.encode(listOf(attribution(7, "Tea")))).isEqualTo("7:3:Tea")
        assertThat(TimerAttributionCodec.encode(listOf(attribution(1, "a"), attribution(2, "bc")))).isEqualTo("1:1:a2:2:bc")
    }

    @Test
    fun encodeOfNothingIsEmptyAndDecodesBack() {
        assertThat(TimerAttributionCodec.encode(emptyList())).isEmpty()
        assertThat(TimerAttributionCodec.decode("")).isEmpty()
    }

    @Test
    fun decodeOfNullIsEmpty() {
        assertThat(TimerAttributionCodec.decode(null)).isEmpty()
    }

    @Test
    fun aMalformedTailReturnsTheEntriesBeforeIt() {
        val valid = TimerAttributionCodec.encode(listOf(attribution(1, "Tea"), attribution(2, "Eggs")))

        assertThat(TimerAttributionCodec.decode("$valid garbage")).containsExactly(attribution(1, "Tea"), attribution(2, "Eggs")).inOrder()
        assertThat(TimerAttributionCodec.decode("${valid}9:")).containsExactly(attribution(1, "Tea"), attribution(2, "Eggs")).inOrder()
        assertThat(TimerAttributionCodec.decode("1:3:Tea2:x")).containsExactly(attribution(1, "Tea"))
    }

    @Test
    fun aLengthLongerThanTheRemainingTextStopsDecoding() {
        assertThat(TimerAttributionCodec.decode("1:99:short")).isEmpty()
        assertThat(TimerAttributionCodec.decode("1:5:Te")).isEmpty()
        assertThat(TimerAttributionCodec.decode("1:3:Tea2:5:Ea")).containsExactly(attribution(1, "Tea"))
    }

    @Test
    fun negativeOrNonNumericLengthOrIdStopsDecoding() {
        assertThat(TimerAttributionCodec.decode("1:-3:abc")).isEmpty()
        assertThat(TimerAttributionCodec.decode("1:+3:abc")).isEmpty()
        assertThat(TimerAttributionCodec.decode("1:x:abc")).isEmpty()
        assertThat(TimerAttributionCodec.decode("x:3:abc")).isEmpty()
        assertThat(TimerAttributionCodec.decode("-1:3:abc")).isEmpty()
        assertThat(TimerAttributionCodec.decode("0:3:abc")).isEmpty()
        assertThat(TimerAttributionCodec.decode("1:99999999999999999999:abc")).isEmpty()
        assertThat(TimerAttributionCodec.decode(":3:abc")).isEmpty()
        assertThat(TimerAttributionCodec.decode("1:")).isEmpty()
        assertThat(TimerAttributionCodec.decode("1")).isEmpty()
    }
}
