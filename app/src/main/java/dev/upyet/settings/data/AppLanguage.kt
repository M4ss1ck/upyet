package dev.upyet.settings.data

/**
 * The languages the in-app picker offers. [SYSTEM] carries no tag: it means "no per-app override", which
 * is how the platform stores "follow the device locale".
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    SPANISH("es"),
    ;

    companion object {
        /**
         * Matches on the language subtag only, so a stored "en-US" or "es-419" still resolves to the
         * option the user picked. A tag we do not translate resolves to [SYSTEM] rather than inventing
         * an option the picker cannot show.
         */
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.substringBefore('-')?.takeIf { it.isNotBlank() } ?: return SYSTEM
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}
