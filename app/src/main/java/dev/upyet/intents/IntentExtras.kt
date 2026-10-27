package dev.upyet.intents

import android.net.Uri
import android.os.Bundle
import androidx.core.os.BundleCompat

/**
 * The slice of an Intent's extras the parser reads. The parser takes this instead of an `Intent`, so parsing stays a
 * pure JVM function that unit tests drive with a plain map.
 */
interface IntentExtras {
    fun has(key: String): Boolean

    /** Null when the key is absent or its value is not an Int. Callers that must tell those apart use [has]. */
    fun int(key: String): Int?

    fun boolean(key: String): Boolean?

    fun string(key: String): String?

    /** Accepts the `ArrayList<Integer>` or `int[]` shapes an Intent extra can carry. Null when neither. */
    fun intList(key: String): List<Int>?
}

/**
 * [IntentExtras] over a real [Bundle]. The untyped `Bundle.get` is deprecated, so reads use the typed getters and
 * check [Bundle.containsKey] first, which is what makes "absent" distinguishable from "present and zero or false".
 * A wrong-typed primitive still reads as its default, because the typed getter cannot report it; the parser treats
 * that the same as a bad value wherever a bad value is an error.
 * `RINGTONE` is read as a string, but some callers put a [Uri] there, so a Uri is accepted too.
 */
class BundleIntentExtras(private val bundle: Bundle?) : IntentExtras {
    override fun has(key: String): Boolean = bundle?.containsKey(key) == true

    override fun int(key: String): Int? = if (has(key)) bundle?.getInt(key) else null

    override fun boolean(key: String): Boolean? = if (has(key)) bundle?.getBoolean(key) else null

    override fun string(key: String): String? {
        if (!has(key) || bundle == null) return null
        return bundle.getString(key)
            ?: BundleCompat.getParcelable(bundle, key, Uri::class.java)?.toString()
    }

    override fun intList(key: String): List<Int>? {
        if (!has(key) || bundle == null) return null
        return bundle.getIntegerArrayList(key)?.toList() ?: bundle.getIntArray(key)?.toList()
    }
}
