# Where should the world clock's city list come from?

Research for [#7](https://github.com/M4ss1ck/upyet/issues/7), part of the Clock-replacement map
([#5](https://github.com/M4ss1ck/upyet/issues/5)). Researched 2026-10-24 against primary sources. minSdk 26.

## Answer

**Use the platform's ICU time zone data. Bundle no city dataset.**

The picker lists every canonical, location-bound time zone the device knows (about 420), each named by its
localized CLDR *exemplar city* (`TimeZoneNames.getExemplarLocationName`) and its localized country
(`Locale.getDisplayCountry`). Search matches city or country, accent- and case-insensitively, with an ICU
`Collator` at primary strength. A saved world-clock city is stored as its canonical time zone ID; the
display name is computed at render time in the current locale, so a language change needs no migration.

Cost: zero APK bytes, zero license obligations, Spanish names for every zone on every supported Android
release, and tz updates arrive with the OS. Gap: large cities that share a zone without being its exemplar
(San Francisco, Barcelona, Mumbai) are not found by name; searching their country finds their zone.

## Findings

### 1. The platform APIs exist at minSdk 26

All on `android.icu`, added in API 24:

| API | What it gives | Source |
|---|---|---|
| `TimeZone.getAvailableIDs()` | every system zone ID | [android.icu.util.TimeZone][tz] |
| `TimeZone.getCanonicalID(id)` | canonical ID, folds aliases such as `Asia/Calcutta` | [android.icu.util.TimeZone][tz] |
| `TimeZone.getRegion(id)` | ISO 3166 region, or `001` for zones not tied to a location | [android.icu.util.TimeZone][tz] |
| `TimeZoneNames.getInstance(Locale)` + `getExemplarLocationName(tzID)` | localized city name | [android.icu.text.TimeZoneNames][tzn] |
| `Collator` | locale-aware, accent-insensitive comparison | [android.icu.text.Collator][coll] |

`getExemplarLocationName` documents a fallback: when the locale has no localized name it derives one from
the ID ("Check if the ID contains '/' ... Replace '_' with ' '", "New York" for `America/New_York`) and
returns null for `Etc/` and `SystemV/` IDs. CLDR specifies the same fallback in
[UTS #35, Time Zone Names][tr35] ("use as the exemplar city the last field of the raw TZID, turning _ into
space").

**Trap.** The filtered overload `getAvailableIDs(SystemTimeZoneType, String, Integer)` is API 24, but the
reference lists every enum constant (`ANY`, `CANONICAL`, `CANONICAL_LOCATION`) as "Added in API level 28"
([SystemTimeZoneType][stt]). Lint's `NewApi` reads that same API database, and this repo builds with
`warningsAsErrors`, so do not use `CANONICAL_LOCATION` on API 26-27. Build the same filter from API 24
methods instead: `getAvailableIDs()`, keep `id == getCanonicalID(id)`, drop `getRegion(id) == "001"`.

### 2. Coverage and Spanish localization are complete

Android ships ICU with CLDR data. The floor for this app is Android 8.0 (API 26-27): ICU 58.2, CLDR 30.0.3;
Android 15 ships ICU 75.1, CLDR 45 ([Unicode and internationalization support][i18n], table 3).

Counted with a script over CLDR's own files (`common/bcp47/timezone.xml` for canonical zones,
`common/main/es.xml` for Spanish names), counting zones with a non-`Etc/` canonical ID:

| CLDR version | Canonical location zones | With an `es` exemplar city entry |
|---|---|---|
| 30.0.3 ([tag `release-30-0-3`][cldr30]) - Android 8.0 | 422 | 422, all spelled out |
| `main` ([CLDR repo][cldrmain]) - current | 418 | 418: 157 spelled out, 261 `↑↑↑` |

`↑↑↑` is CLDR's "same as the inherited value" marker: the name resolves from `root.xml` (which now holds
`Tirana`, `Bogotá`, `Canarias`) or from the ID (`Andorra`, `Kabul`). Those are the names CLDR 30 spelled
out in `es.xml` for the same zones, so the resolved Spanish result is the same.

Samples from `es.xml`: `Africa/Abidjan` → Abiyán, `America/Havana` → La Habana, `Europe/London` → Londres,
`Asia/Tokyo` → Tokio, `America/New_York` → Nueva York, `Atlantic/Canary` → Islas Canarias (CLDR 30).

### 3. What AOSP DeskClock does: a hand-curated, bundled list

DeskClock does not use ICU names. It ships its own city list as string resources
([`res/values/cities.xml`][dc-cities], Apache-2.0 header, Copyright 2016 AOSP):

- 340 entries, each a string like `A=Abidjan|Africa/Abidjan` (index letter, name, optional phonetic name,
  zone ID), enumerated by the `city_ids` array.
- Translated per locale: 84 `values-*/cities.xml` files in `res/`, including
  [`values-es/cities.xml`][dc-es] ("Nueva York", "Londres", "Abiyán", "Nueva Orleans").
- Parsed by [`CityDAO.kt`][dc-dao], which splits on `|` and `=`/`:`, resolves the zone with
  `java.util.TimeZone.getTimeZone`, and skips cities whose zone resolves to GMT.

Compared against the zones (script output, English names):

- The 340 cities cover only 254 distinct zone IDs, fewer than the ~420 ICU offers.
- About 120 entries are not their zone's exemplar city. Most are added value: San Francisco, Seattle,
  Boston, Barcelona, Munich, Mumbai, Delhi, Rio de Janeiro, Cape Town.
- The list is hand-maintained and has drifted. `Port Moresby` maps to `Pacific/Palau` (UTC+9); Port Moresby
  is UTC+10 (`Pacific/Port_Moresby`). It also uses legacy and alias IDs (`Asia/Calcutta`, `Australia/NSW`,
  `Europe/Belfast`, `MST`).
- It carries US-tech-office picks (Mountain View, Kirkland, Reston, Black Rock City).

Reusing it would be license-compatible (Apache-2.0 is GPLv3-compatible per the [FSF license list][fsf-apache]),
but we would inherit its errors and own 85 translated files that ICU already maintains for us.

### 4. Bundled dataset option: GeoNames

The obvious open dataset is [GeoNames][geonames] (readme: "licensed under a Creative Commons Attribution
4.0 License"). CC BY 4.0 is compatible with all GNU GPL versions per the [FSF license list][fsf-ccby]
(attribution required).

- `cities15000.zip` ("all cities with a population > 15000 or capitals (ca 25.000)"): 3,363,204 bytes
  compressed (HTTP `Content-Length`, fetched 2026-10-24). Each row carries its IANA zone.
- Spanish names are not in it. They live in `alternateNamesV2.zip`, 205,159,418 bytes compressed, so we
  would need a build-time extraction pipeline, a refresh process, and a Spanish name for each of 25k
  places that GeoNames does not guarantee.

25,000 cities is not what a world clock needs. Users add a handful of places; the question each answers
is "what time is it there", which is per zone, not per town.

## Options compared

| | Platform ICU (recommended) | DeskClock list (Apache-2.0) | GeoNames cities15000 (CC BY 4.0) |
|---|---|---|---|
| APK cost | 0 | ~76 KB default + 84 locale files | ~3.4 MB compressed before trimming |
| Zones covered | all ~420 | 254 | all |
| Spanish | every zone, from CLDR | yes, frozen translations | needs 205 MB alternate-names pipeline |
| Non-exemplar big cities | no (country search instead) | ~120 | yes |
| Maintenance | OS tz/ICU updates | ours, already wrong in places | ours, refresh pipeline |
| License work | none | NOTICE attribution | attribution |

## Recommendation for the spec

1. City identity is the canonical IANA zone ID. Persist that, never a display name.
2. Picker source: `getAvailableIDs()` filtered to canonical IDs with a real region. Name with
   `TimeZoneNames.getInstance(locale).getExemplarLocationName(id)`; subtitle with the country from
   `Locale("", TimeZone.getRegion(id)).getDisplayCountry(locale)` and the current UTC offset.
3. Search city and country names with `Collator` at `PRIMARY` strength, so "bogota" finds "Bogotá"
   and "espana" finds "España" (Madrid, Ceuta and the Canary Islands zone).
4. On load, re-canonicalize saved IDs with `getCanonicalID`, so a zone that later becomes an alias still
   resolves.
5. No bundled dataset. If testing shows the missing alias cities matter, add a small hand-curated alias
   table (city name → zone ID, a few dozen entries) as string resources, translated like any other string.
   That is a follow-up decision, not part of v1.

## Open questions

- **Device verification (unverified).** These claims come from docs and CLDR source, not a device. Before
  the build slice ships, run on an API 26 emulator and a current one: count of filtered IDs, `es` names for
  ten zones, and that `Collator` search matches unaccented input. AOSP may trim ICU locale data; confirm the
  `es` zone strings are present on a real API 26 image.
- **OEM ICU data.** Device makers can replace ICU data. Not researched; the ID-derived fallback is the
  floor if a name is missing.
- **Alias cities.** Whether San Francisco-style lookups are needed for v1 is a product call for #5.
- **Lint vs docs on `SystemTimeZoneType`.** Not tested whether lint actually flags `CANONICAL_LOCATION`
  below 28. The recommended filter avoids the question.

## Sources

- [android.icu.util.TimeZone][tz]
- [android.icu.util.TimeZone.SystemTimeZoneType][stt]
- [android.icu.text.TimeZoneNames][tzn]
- [android.icu.text.Collator][coll]
- [Unicode and internationalization support (ICU/CLDR per Android version)][i18n]
- [UTS #35 Part 4, Dates: Time Zone Names][tr35]
- [CLDR `es.xml`, `main`][cldrmain] and [CLDR 30.0.3][cldr30]
- [AOSP DeskClock `res/values/cities.xml`][dc-cities], [`res/values-es/cities.xml`][dc-es], [`CityDAO.kt`][dc-dao]
- [GeoNames dump readme][geonames]
- FSF license list: [Apache 2.0][fsf-apache], [CC BY 4.0][fsf-ccby]

[tz]: https://developer.android.com/reference/android/icu/util/TimeZone
[stt]: https://developer.android.com/reference/android/icu/util/TimeZone.SystemTimeZoneType
[tzn]: https://developer.android.com/reference/android/icu/text/TimeZoneNames
[coll]: https://developer.android.com/reference/android/icu/text/Collator
[i18n]: https://developer.android.com/guide/topics/resources/internationalization
[tr35]: https://www.unicode.org/reports/tr35/tr35-dates.html#Time_Zone_Names
[cldrmain]: https://github.com/unicode-org/cldr/blob/main/common/main/es.xml
[cldr30]: https://github.com/unicode-org/cldr/blob/release-30-0-3/common/main/es.xml
[dc-cities]: https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/res/values/cities.xml
[dc-es]: https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/res/values-es/cities.xml
[dc-dao]: https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/src/com/android/deskclock/data/CityDAO.kt
[geonames]: https://download.geonames.org/export/dump/readme.txt
[fsf-apache]: https://www.gnu.org/licenses/license-list.html#apache2
[fsf-ccby]: https://www.gnu.org/licenses/license-list.html#ccby
