package dev.upyet.worldclock.domain

import dev.upyet.worldclock.data.WorldCity
import java.text.Normalizer
import java.util.Locale

/**
 * Accent-insensitive offline search over the bundled gazetteer (issue #15: city and country, sorted by population).
 *
 * The keys are normalized once here, not per query: a query runs over about 5,700 cities on the UI thread while the
 * user types, and re-folding every name on each keystroke is the cost this class exists to avoid.
 *
 * Ranking is a tier first and population second. A user typing "san" wants the cities that start with it ahead of
 * the big cities where it only occurs mid-word ("Pusan"), so the tier outranks population.
 */
class CitySearchIndex(cities: List<WorldCity>, countryNames: (isoA2: String) -> Collection<String>) {
    private class Entry(
        val city: WorldCity,
        val name: String,
        val nameEs: String,
        /** Word-start probes are `" " + word`, so a leading space makes a match at the first word count too. */
        val paddedName: String,
        val paddedNameEs: String,
        /** Each haystack is a string the query may be a substring of: names, countries and "name country" pairs. */
        val haystacks: List<String>,
    )

    private val entries: List<Entry>

    init {
        // Country names repeat across ~5,700 cities; look each ISO code up once.
        val countriesByIso = HashMap<String, List<String>>()
        entries = cities
            .sortedWith(compareByDescending<WorldCity> { it.population }.thenBy { it.geonamesId })
            .map { city ->
                val countries = countriesByIso.getOrPut(city.isoA2) {
                    countryNames(city.isoA2).map { normalize(it) }.filter { it.isNotEmpty() }
                }
                val name = normalize(city.name)
                val nameEs = normalize(city.nameEs)
                Entry(
                    city = city,
                    name = name,
                    nameEs = nameEs,
                    paddedName = " $name",
                    paddedNameEs = " $nameEs",
                    haystacks = buildList {
                        add(name)
                        add(nameEs)
                        for (country in countries) {
                            add(country)
                            add("$name $country")
                            add("$nameEs $country")
                        }
                    },
                )
            }
    }

    fun search(query: String, limit: Int = MAX_RESULTS): List<WorldCity> {
        val needle = normalize(query)
        if (needle.isEmpty()) return emptyList()
        val wordStart = " $needle"

        val prefix = mutableListOf<WorldCity>()
        val wordStarts = mutableListOf<WorldCity>()
        val contained = mutableListOf<WorldCity>()
        for (entry in entries) {
            when {
                entry.name.startsWith(needle) || entry.nameEs.startsWith(needle) -> prefix += entry.city
                entry.paddedName.contains(wordStart) || entry.paddedNameEs.contains(wordStart) -> wordStarts += entry.city
                entry.haystacks.any { it.contains(needle) } -> contained += entry.city
            }
        }
        return (prefix + wordStarts + contained).take(limit)
    }

    companion object {
        const val MAX_RESULTS = 50

        private val NON_LETTER_OR_DIGIT = Regex("[^\\p{L}\\p{Nd}]+")
        private val COMBINING_MARK = Regex("\\p{Mn}")

        /**
         * Folds accents, case and punctuation so "São Paulo", "sao paulo" and "SAO-PAULO" compare equal. NFD splits
         * each accented letter into base letter plus combining mark, so dropping the marks keeps the base letter.
         */
        fun normalize(text: String): String {
            val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
            return COMBINING_MARK.replace(decomposed, "")
                .lowercase(Locale.ROOT)
                .replace(NON_LETTER_OR_DIGIT, " ")
                .trim()
        }
    }
}
