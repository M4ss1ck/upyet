package dev.upyet.intents

/** A plain-map [IntentExtras] for JVM tests. Values are typed the way an Intent stores them: Integer, ArrayList, int[]. */
class MapIntentExtras(private val map: Map<String, Any?>) : IntentExtras {
    override fun has(key: String): Boolean = map.containsKey(key)

    override fun int(key: String): Int? = map[key] as? Int

    override fun boolean(key: String): Boolean? = map[key] as? Boolean

    override fun string(key: String): String? = map[key] as? String

    override fun intList(key: String): List<Int>? = when (val value = map[key]) {
        is ArrayList<*> -> value.map { it as? Int ?: return null }
        is IntArray -> value.toList()
        else -> null
    }
}
