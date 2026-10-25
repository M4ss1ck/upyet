# Which offline dataset should back world clock search and map markers?

Research for issue #21, 2026-10-24. Produced by an opencode researcher (deepseek-v4.1-flash, web access); load-bearing counts re-verified by the supervisor against `ne_10m_populated_places.geojson`: 7,342 rows, 5,764 at POP_MAX >= 15,000, 466 of those with no TIMEZONE (Sydney, Moscow), NAME_ES on all 5,764. Unverified: GeoNames zip contents (the researcher could not unzip; claims rest on readme + per-record pages).

## Verified vs. inferred

Everything below is measured/fetched in this session unless marked *(inference)* or *(unverified)*. Huge caveat up front: this environment can fetch but **cannot unzip**, so the GeoNames `.zip` payloads could not be opened — only the directory listing, the readme and individual geonames.org records were readable.

---

# 1. Candidate: Natural Earth — populated places

**Licence.** Public domain. https://www.naturalearthdata.com/about/terms-of-use/ says verbatim: *"All versions of Natural Earth raster + vector map data found on this website are in the public domain… No permission is needed to use Natural Earth. Crediting the authors is unnecessary."* The same page lists the third-party permissions (Washington Post, EC JRC IES, XNR, International Mapping Associates) and states the name data for 20+ languages comes from **Wikidata, CC0**. FSF on public domain: https://www.gnu.org/licenses/license-list.en.html (CC0 entry) — *"Both public domain works and the lax license provided by CC0 are compatible with the GNU GPL."* **Compatible with GPL-3.0-or-later: yes.**

**Attribution duty: none.** Requested, not required: *"Made with Natural Earth."*

**Rows** (measured from the repo GeoJSON): **ne_10m_populated_places = 7,342**; **ne_50m = 1,251**; **ne_110m = 243**.

**Fields** (137 properties on the 10m file): `NAME`, `NAMEASCII`, `NAME_EN`, `NAME_ES` + 24 more `NAME_xx` (`de, fr, pt, ru, zh, zht, ar, bn, el, hi, hu, id, it, ja, ko, nl, pl, sv, tr, vi, fa, he, uk, ur`), `ISO_A2`, **`TIMEZONE` (IANA id)**, `LATITUDE`, `LONGITUDE`, `POP_MAX`, `POP_MIN`, `GEONAMESID`, `WIKIDATAID`, `ADM1NAME`, `SOV0NAME`. So: zone ✓, lat/lon ✓, population ✓, country ✓, **Spanish ✓ (and English via `NAMEASCII`/`NAME_EN`)**.

⚠️ **The `_simple` variant is unusable**: 7,342 rows but its 31 fields contain **no `TIMEZONE` and no `NAME_xx`** (verified). Only the full theme works.

**Size.** `ne_10m_populated_places.zip` = **2,811,199 B (2.68 MB)**, last-modified **2022-05-13** (HEAD on `naciscdn.org`). GeoJSON form = 19,359,003 B. `_simple` zip = 636.84 KB.

**Stripped asset** (measured on the raw rows, TSV, UTF-8):
| rows | columns | bytes |
|---|---|---|
| 5,764 (POP_MAX ≥ 15,000) | ascii + es + iso + tz + lat + lon + pop | **326,885** |
| 5,764 | same minus `es` | **274,244** |
| 7,342 | ascii + es + … | 476,686 |
| 7,342 | ascii + iso + tz + lat + lon + pop | 311,856 |

Compressed *(inference)*: 3.0–3.6× is the deflate ratio I measure on Natural Earth's own artefacts (50m land: 1,636,166 B geojson → 457,183 B zip = 3.58×; 110m land: 138,160 → 69,700 = 1.98×), so **~95–125 KB deflated** for the ≥15k, with-`es` asset. Definitive figure: **~325 KB raw / ~110 KB in the APK**.

⚠️ **`NAME_ES` is good but not complete.** Verified: Nueva York, Tokio, El Cairo, Pekín, Roma, Lisboa, Bruselas, Múnich, Edimburgo, Bombay — but **London → "London"** (no Spanish exonym). `NAME_ES` differs from ASCII on 2,041 of 7,342 rows.

⚠️ **Duplicate names are real**: "San Francisco" exists twice (AR pop 59,062 / **US** pop 3,450,000), "Barcelona" twice (VE 600,954 / **ES** 4,920,000), 199 ASCII names duplicated across 457 rows. Search must show the country.

### Zone ids in Natural Earth — the decisive finding

- 320 distinct `TIMEZONE` values; **0 unknown** — every one exists in current tzdb as a Zone or Link.
- **1,167 of the 6,159 rows carrying a zone (19%) carry a *backward Link*, not a canonical zone**: `Europe/Amsterdam`, `Europe/Oslo`, `Asia/Kuala_Lumpur`, `America/Montreal`, `Asia/Chongqing`, `Asia/Harbin`, `Asia/Kashgar`, `Europe/Kiev`, `Asia/Rangoon`, `America/Godthab`, `America/Yellowknife`, `Pacific/Enderbury`, …
- **One row uses a `backzone`-only id: `Pacific/Ponape`** — and `backzone` is explicitly *not* part of a default tzdb build ("Zones that go back beyond the scope of the tz database"). It must be rewritten to `Pacific/Pohnpei` at build time.
- Relation to Android: Android's tzdb is IANA-derived and updated out-of-band via the `com.google.android.tzdata*` APEX — https://source.android.com/docs/core/architecture/modular-system/timezone and https://source.android.com/docs/core/permissions/timezone-rules: *"IANA releases an update… Google or the Android partner prepares a Time Zone Data module"*, and libcore/bionic/ICU all consume it. Consequence: link ids are safe (AOSP DeskClock itself ships `Asia/Calcutta` and `Asia/Tel_Aviv`), `backzone`-only ids are not, and the device's tzdb may be **newer** than the app's bundled mapping — so ids must be validated against a pinned tzdb and never assumed to be the only ids the device has.

### 🚨 `TIMEZONE` is incomplete — this breaks NE as a sole source

**1,181 of 7,342 rows (16.1%) have no zone; 466 of the 5,764 rows at ≥15,000 (8.1%).** The missing ones include, verbatim from the file:

`Moscow (RU) 10.4M`, `Sydney (AU) 4.63M`, `Houston (US) 4.46M`, `Melbourne (AU) 4.17M`, `Hechi (CN) 3.83M`, `Irvine (US)`, `Denver (US)`, `Pittsburgh (US)`, `Vila Velha (BR)`, `Natal (BR)`, `Noginsk (RU)`, `Lancaster (US)`, … 205 of the 466 are in multi-zone countries, 261 in single-zone countries, 3 have no country code.

I tested the obvious cheap fixes and **they do not work alone**:

- **GeoNames join by `GEONAMESID`**: 244 of the 466 gap rows have an id; **Moscow (524901) → Europe/Moscow ✓, Houston (4699066) → America/Chicago ✓, Denver (5419384) → America/Denver ✓, Pittsburgh (5206379) → America/New_York ✓, Waterbury → America/New_York ✓, Lancaster → America/New_York ✓, Tulcan → America/Guayaquil ✓, Krasnoturinsk → Asia/Yekaterinburg ✓, Naberezhnyye Chelny → Europe/Moscow ✓, Zaozernyy → Asia/Krasnoyarsk ✓** (10/10 correct). But **Sydney's NE row has `GEONAMESID = -1`**, and `Melbourne` resolves to the *Melbourne, US* namesake — the id join alone leaves ~108 rows stranded, Sydney included.
- **Nearest same-country known zone**: I computed it and it is **unsafe**: `Noginsk (RU) → Asia/Krasnoyarsk`, `Vila Velha (BR) → America/Belem`, `Natal (BR) → America/Manaus`, `Lancaster (US) → America/Los_Angeles`. Do not ship this heuristic.
- **Single-zone country from tzdata `zone.tab`**: exact, and covers 261 of the 466 (all sample gaps — VN, EG, KW, GT, AF, SY, NO — are single-zone countries).

**Cadence: dead.** Latest GitHub release is **v5.1.2, published 2022-05-13** (GitHub releases API); the CHANGELOG has a `2022-06-02: 5.2.0` entry but no tag and no published files, and the CDN artefacts are dated 2022-05-13. **No new data in ~4.4 years.**

**Coverage check (10m, full file):**

| city | present | row used | zone | es |
|---|---|---|---|---|
| San Francisco | ✓ | US, pop 3,450,000 | `America/Los_Angeles` | San Francisco |
| Barcelona | ✓ | ES, pop 4,920,000 | `Europe/Madrid` | Barcelona |
| Mumbai | ✓ | pop 18,978,000 | `Asia/Kolkata` | **Bombay** |
| Seattle | ✓ | pop 3,074,000 | `America/Los_Angeles` | Seattle |
| Munich | ✓ | pop 1,275,000 | `Europe/Berlin` | **Múnich** |
| Osaka | ✓ **only as `NAME = "Ōsaka"`, `NAMEASCII = "Osaka"`** | pop 11,294,000 | `Asia/Tokyo` | Osaka |

The Ōsaka case is a design constraint, not a detail: **search must match on an ASCII-folded key**, exactly the diacritic-insensitive matching issue #7 already decided for ("espana" → España).

---

# 2. Candidate: GeoNames

**Licence.** CC BY 4.0. https://download.geonames.org/export/dump/readme.txt: *"This work is licensed under a Creative Commons Attribution 4.0 License, see https://creativecommons.org/licenses/by/4.0/"*. https://www.geonames.org/export/ adds: *"cc-by licence… You should give credit to GeoNames when using data or web services with a link or another reference to GeoNames."* FSF (https://www.gnu.org/licenses/license-list.en.html, #ccby): *"Creative Commons Attribution 4.0 license (a.k.a. CC BY) … It is compatible with all versions of the GNU GPL; however, like all CC licenses, it should not be used on software."* **GPL-3.0-or-later compatible: yes.**

**Attribution duty: yes, mandatory** — a credit and a link to geonames.org must ship wherever the data is used, including derived build-time output.

**Rows** (readme, exact counts unverified — see CONCERNS): `cities15000` *"population > 15000 or capitals (ca 25.000)"*; `cities5000` *"population > 5000 or PPLA (ca 50.000)"*; `cities1000` ≈130,000; `cities500` ≈185,000.

**Fields** (19, tab-separated, documented in the same readme): `geonameid, name, asciiname, alternatenames, latitude, longitude, feature class, feature code, country code, cc2, admin1–4, population, elevation, dem, **timezone (the iana timezone id)**, modification date`. **No localized names in the city dumps** — those live only in `alternateNamesV2.zip` (`isolanguage` column `es`/`en`).

**Size** (measured from the directory listing, timestamps 2026-10-24): `cities15000.zip` **3.2 M**, `cities5000.zip` **5.5 M**, `cities500.zip` 13 M, `allCountries.zip` 403 M, **`alternateNamesV2.zip` 196 M**. Stripped 6-column asset *(inference)*: ~25k rows × ~50 B ≈ **1.2–1.4 MB raw / ~350–450 KB deflated**; at ≥100,000 population it would land near NE's size.

**Zone ids**: documented as IANA; **canonical** in every case I checked. **Update cadence: daily** (files regenerated 2026-10-24; `modifications-2026-10-23.txt`, `deletes-2026-10-23.txt` present).

**Coverage (verified on geonames.org records, incl. their `Timezone` field and administrative population):**

| city | geonameid | page population | zone |
|---|---|---|---|
| San Francisco | 5391959 | 827,526 | `America/Los_Angeles` |
| Barcelona | 3128760 | 1,686,208 | `Europe/Madrid` |
| Mumbai | 1275339 | 12,691,836 | `Asia/Kolkata` |
| Seattle | 5809844 | 780,995 | `America/Los_Angeles` |
| Munich | 2867714 | 1,505,005 | `Europe/Berlin` |
| Osaka | 1853909 | 2,753,862 | `Asia/Tokyo` |

All six present, all correct. Spanish names: **only via the 196 MB `alternateNamesV2` pipeline** — the exact objection issue #7 already recorded.

---

# 3. Candidate: IANA tzdata (`zone1970.tab` / `zone.tab`)

**Licence.** Public domain. `zone1970.tab` header: *"This file is in the public domain."* `LICENSE`: *"Unless specified below, all files in the tz code and data (including this LICENSE file) are in the public domain."* FSF-compatible: yes. **No attribution duty.**

**Rows** (measured): **`zone1970.tab` = 312 data rows, 312 unique zones, 17,536 B**. **`zone.tab` = 418 rows across 247 countries** (216 single-zone, 31 multi-zone; US = 29 rows, ES = 3). Current tzdb as a whole: **467 Zones + 250 Links = 717 names** (computed from `africa…etcetera`, `backward`, `backzone`), of which 243 Links are in `backward` and 136 names are `backzone`-only.

**Fields**: country codes, **`±DDMM±DDDMM` coordinates of the zone's principal location** (ISO 6709), zone name, comment. **No city name, no population, no localized names.** So: vocabulary ✓, coords-of-zone ✓, city list ✗.

**Zone ids**: canonical by definition; this is the reference Android's tzdb is built from. **Cadence**: several releases a year (source.android.com notes *"TZDB 2026c was the last patch"* for Android 10 support).

**Coverage of the six cities**: the *zones* are all canonical and present (`America/Los_Angeles`, `Europe/Madrid`, `Asia/Kolkata`, `Europe/Berlin`, `Asia/Tokyo`), with principal-location coordinates; the *cities* are not in the file. This is a validation/vocabulary source, not a city source — precisely issue #7's outcome.

---

# 4. Candidate: AOSP DeskClock `cities.xml`

**Licence.** Apache-2.0 (file header: *"Copyright (C) 2016 The Android Open Source Project… Apache License, Version 2.0"*), https://android.googlesource.com/platform/packages/apps/DeskClock/+/refs/heads/main/res/values/cities.xml. FSF: *"Apache License, Version 2.0 … This is a free software license, compatible with version 3 of the GNU GPL."* **Compatible: yes.**

**Attribution duty: yes** — Apache-2.0 §4 requires keeping the NOTICE and the licence text for redistributed derived data. (Copying the file verbatim into a GPL-3 app is *permitted* — Apache-2.0 is GPLv3-compatible — but the notice must travel with it.)

**Rows / fields** (measured): **340 entries, 260 distinct zones, 75,956 B**. Format is a packed string `"<index char>=<City>|<ZoneId>"` inside a `city_ids` `<array>`; some entries carry a phonetic part `City:Phonetic|Zone`. Fields: **city name + zone id only — no coordinates, no population, no country code**.

**Localized names**: a real strength — `res/values-es/cities.xml` exists, 54,106 B, **all 340 entries translated** (`Abiyán`, `Acra`, `Adís Abeba`, `Argel`…). Issue #7's "84 translated cities.xml files" holds up in spirit.

**Zone ids are *not* canonical**: the list carries `Asia/Calcutta`, `Asia/Tel_Aviv`, `Canada/Newfoundland`, `Canada/Saskatchewan`, `Chile/EasterIsland`, and the legacy zone `MST` — all resolvable backward links (verified in `backward`), fine at runtime but a sign the list is hand-maintained.

**Drift confirmed**: `Port Moresby → Pacific/Palau` (UTC+9 instead of +10) — exactly the defect issue #7 recorded. `Pacific/Palau` is Palau's own zone in `zone1970.tab`.

**Coverage**: all six cities are present and correctly zoned (San Francisco/Seattle → `America/Los_Angeles`, Barcelona → `Europe/Madrid`, Mumbai → `Asia/Kolkata`, Munich → `Europe/Berlin`, Osaka → `Asia/Tokyo`). *My first pass reported Osaka and Tokyo missing — that was a parser artefact of the `City:Phonetic|Zone` form; both are present (C259, C139).*

**Cadence**: none documented; hand-curated in AOSP main, no release contract, no changelog. 340 cities is not a searchable gazetteer — the issue's own premise ("San Francisco, Barcelona, Mumbai found directly") is only half met by a list this small.

---

# 5. Land outline

| asset | polygons / points | raw | zip | note |
|---|---|---|---|---|
| **ne_110m_land** | 127 polygons, 128 rings, **5,143 points** | 138,160 B GeoJSON | **69,700 B** (2021-09-04) | public domain, right detail for a ~300 dp map |
| ne_50m_land | **1,420 polygons, 60,669 points** | 1,636,166 B | **457,183 B** | over-detailed unless simplified |
| world-atlas `land-110m.json` | TopoJSON | **18,138 B** | — | NE-derived, tiny, but needs a TopoJSON decoder + a third-party repo |

**Ruled out with evidence:** Natural Earth's own `ne_10m_time_zones` theme is **keyed by UTC offset, not IANA id** — its properties are `zone: -10`, `utc_format: "UTC-10:00"`, `tz_name1st: null`. It cannot resolve a coordinate to `America/Los_Angeles` (2.0 MB zip, 2021-12-08). And OSM-derived `timezone-boundary-builder` is **ODbL**, which is not GPL-compatible, so it is out under §15/License.

**Recommendation for the map**: ship **`ne_110m_land`** converted at build time to a compact binary — 5,143 points quantised to 1/100° in `int16` pairs = **20,572 B**, plus ring offsets; ~15 KB deflated, comfortably small enough for Compose `Canvas` `Path` built once per screen size. 110m land has exactly 1 hole (Caspian) and no antimeridian surprises.

---

# 6. One recommended combination

> **City list, names, coordinates, population: Natural Earth `ne_10m_populated_places` (POP_MAX ≥ 15,000).**
> **Zone resolution: NE `TIMEZONE` → tzdata single-zone-country → GeoNames `cities15000` by `GEONAMESID`, then by `(asciiname, ISO2)`; drop-with-report otherwise.**
> **Zone vocabulary & validation: IANA tzdata, pinned.**
> **Map: Natural Earth `ne_110m_land`, 110m.**
> **Display names for the *zone's own* city: keep issue #7's ICU/CLDR exemplar names (`android.icu.text.TimeZoneNames`), which give "Londres" where NE gives "London".**

**Why this and not GeoNames-primary.** NE is the only candidate that hands you **Spanish and English names for every row, coordinates, population, country and a zone, at public-domain terms, in one 2.68 MB download** — the "205 MB alternate-names pipeline" objection from issue #7 disappears entirely. GeoNames is *better at zones* (complete, canonical, daily) but *has no Spanish without that 196 MB pipeline*, and a 25k-row GeoNames-primary list is ~1.3 MB raw / ~400 KB deflated for a class of data a clock app barely exercises. Using GeoNames as a **build-time zone oracle only** buys the one thing NE lacks (the 8% zone gap, Sydney/Moscow/Houston included) while the shipped asset stays NE-shaped: **~325 KB raw, ~110 KB compressed (estimate), 5,700-odd cities, 297+ distinct zones, Spanish for 100% of rows, two licences (one of them public domain), no network, no permission.**

- The `en` column is **not needed** — `NAMEASCII` already is the English string (they differ on only 1,670/7,342 rows, mostly diacritics). Ship `ascii` (search key) + `es` (display when locale is `es`; `app/build.gradle.kts:78` filters to exactly `en`/`es`).
- Keep ICU for the zone row label and `Locale.getDisplayCountry` for the country (issue #7), so "Londres"/"España" are right even where NE's Wikidata names are thin.

**Attribution text UpYet must show** (a "Data sources" block in Settings/About, plus `docs/site/privacy.md` and a THIRD_PARTY notices file):

> World clock city data: **Made with Natural Earth.** Free vector and raster map data @ naturalearthdata.com. (Public domain — credit is a courtesy, not an obligation.)
> Place data © **[GeoNames](https://www.geonames.org/)**, licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). (Required: CC BY 4.0 attribution is a licence condition.)
> Time zone rules: IANA Time Zone Database, public domain.

If the team wants **zero** CC BY obligation, the alternative is to drop GeoNames and instead hand-curate the ~108 unresolvable rows — but that is exactly the hand-edit the issue forbids, and it loses Sydney. I do not recommend it.

---

# 7. Deterministic build-time stripping step

**Location.** `scripts/generate-world-clock-data.py` (sibling of `scripts/build-android-release.sh`; there is no `buildSrc` in this repo, and no `res/raw` yet), invoked by a Gradle task and verified by a JVM unit test — §17 requires new behaviour to ship with tests, and a generated asset needs one that pins it.

**Inputs, all pinned by URL + SHA-256 in a checked-in `data-sources.lock`:**

| input | URL |
|---|---|
| Natural Earth populated places | `https://naciscdn.org/naturalearth/10m/cultural/ne_10m_populated_places.zip` (v5.1.2, 2022-05-13) — or the `geojson/` copy at tag `v5.1.2` |
| Natural Earth land | `https://naciscdn.org/naturalearth/110m/physical/ne_110m_land.zip` |
| GeoNames city dump | `https://download.geonames.org/export/dump/cities15000.zip` (pin a dated snapshot; the daily dump is not reproducible) |
| tzdata | `https://raw.githubusercontent.com/eggert/tz/<tag>/` → `zone.tab`, `zone1970.tab`, `backward`, `backzone`, `africa…etcetera` |

**Steps** (pure function of the inputs, no clock, no locale, no network at build-consumer time):

1. Read `ne_10m_populated_places`; keep rows with `POP_MAX >= 15000`. Namespace the `simple` theme is rejected (no `TIMEZONE`, no `NAME_xx`).
2. Normalise `NAMEASCII` to NFC; build `search_key` = lowercase, diacritics folded, punctuation stripped (so `Ōsaka` → `osaka`, `Múnich` → `munich`).
3. Resolve `zone`, in this order, recording which rule fired:
   1. NE `TIMEZONE`, if it resolves in the pinned tzdb **default build** (main files + `backward`); a `backzone`-only id is rewritten to its Link target (`Pacific/Ponape → Pacific/Pohnpei`, `Arctic/Longyearbyen → Europe/Berlin`).
   2. `zone.tab` single-zone country for `ISO_A2` (216 of 247 countries) — exact, no guessing.
   3. `cities15000` by `GEONAMESID` (skip `-1`).
   4. `cities15000` by `(asciiname, ISO2)`, accepted only when the match is unique.
   5. Otherwise **drop the row and print it**; the script exits non-zero if the drop count exceeds a pinned budget (so a data refresh cannot silently gut the list).
4. Reject any zone that does not resolve; optionally canonicalise backward links (all 243 are exact aliases, so either form is correct — canonicalising keeps the stored id set at the 344 main zones and dedupes zone rows in the picker).
5. Deduplicate by `(asciiname, ISO2)` keeping the highest `POP_MAX` (drops 63 duplicate-name rows at ≥15k).
6. Sort by `search_key` **byte order** (not `localeCompare`, which is locale-dependent — my own prototype diverged here).
7. Emit:
   - `app/src/main/res/raw/world_cities.tsv` — columns `search_key \t name \t name_es \t iso2 \t zone \t lat \t lon \t pop`, lat/lon fixed to 3 decimals. **~325 KB raw, ~110 KB deflated inside the APK** (aapt2 compresses `res/raw` by default; do *not* pre-gzip or you pay twice).
   - `app/src/main/res/raw/world_land.bin` — 110m land as `int16` lon/lat pairs at 1/100°, ring-offset table, **20,572 B of coordinates**.
   - `world-data.manifest.txt` — input SHA-256s, tzdata tag, per-rule fill counts, final row count, output SHA-256. Committed, so a data refresh is a reviewable diff.
8. Unit test (`WorldClockDataTest`): loads the generated asset, asserts the count, asserts the ⚠️ six acceptance rows (`San Francisco|US → America/Los_Angeles`, `Barcelona|ES → Europe/Madrid`, `Mumbai → Asia/Kolkata`, `Seattle → America/Los_Angeles`, `Munich → Europe/Berlin`, `Osaka → Asia/Tokyo`), asserts every `zone` in the file resolves via `ZoneId.of`, asserts the output hash equals the manifest. Instrumented test: search for "san francisco", "barcelona", "mumbai", "munich", "mun", "osaka" returns the right row first.

**Fits the invariants**: no `INTERNET` permission is added (the manifest declares only `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `VIBRATE`, `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE{,_MEDIA_PLAYBACK}`, `CAMERA` — `app/src/main/AndroidManifest.xml:5-14`); §12 "Room stores metadata only, never localized display strings" is respected because this is an immutable shipped asset, not Room; §16 is respected because this is data, not a dependency (no Gradle coordinates, nothing in `libs.versions.toml`); Licensing is recorded in the notices, per §15/License (`AGENTS.md:420-424`).

---

