# The world clock ships a bundled gazetteer

**Status: reverses the outcome of issue #7 (platform ICU only, no bundled data).** Issue #7 closed on the
platform's own zone data: label every zone with `TimeZoneNames.getExemplarLocationName`, store the canonical
zone id, search with a `Collator`, and ship no dataset at all. Issue #21 measured what that produces and
found it cannot carry a world clock. This ADR records the reversal, the two sources the feature needs, the
validation that makes the join trustworthy, and how the data is built, shipped, refreshed and attributed.

**ICU can only name a country, or a capital.** `getExemplarLocationName` answers with one name per zone id,
and for most ids that name is a country or its capital. San Francisco, Barcelona, Mumbai and Osaka are not
exemplars of anything, so a picker built on ICU offers "United States" and nothing inside it: a user who
wants the time in a specific city outside the capital cannot find it. The workaround that issue #7 accepted,
finding non-capital cities through their country, is a different feature with a worse question: nobody asks
"what time is it in the United States". A world clock is a list of places, so the app has to ship a list of
places.

**Natural Earth supplies the places, GeoNames supplies the zones, and neither alone is enough.** Natural
Earth's 10m populated places is public domain and carries names, coordinates, population, country and
`NAME_ES`, which is the only cheap route to Spanish place names; it also carries a `TIMEZONE` column, and
that column is the trap. Of the 5,764 rows at or above the 15,000 population floor, 466 (about 8 percent)
carry no zone at all, and 57 sampled rows carry another country's zone, so Lagos resolving to
`Europe/Athens` is not a hypothetical. The zone therefore comes from GeoNames (CC BY 4.0), and Natural Earth
contributes everything except the zone. Its `TIMEZONE` column is ignored, not repaired.

**Every resolved zone is validated against `zone.tab` for that row's country.** GeoNames resolves a zone by
`GEONAMESID`, then by name plus country, then by falling back to the zone of a country tzdata says has
exactly one. A resolution is accepted only when the resulting id appears in the IANA `zone.tab` rows for the
row's own country, after `backward` links are canonicalised, so a row cannot keep a zone its country does
not use. Rejected rows are dropped and counted rather than written. Canonicalising links at build time also
keeps stored ids inside the 344 main zones: of 6,159 Natural Earth rows checked, 1,167 carried a `backward`
link, and storing a link id would make the stored data depend on how long tzdata keeps that alias.

**The asset is committed, the script is manual, and the build never touches the network.** A generator at
`scripts/world-clock/generate.py` reads `scripts/world-clock/data-sources.lock`, which records the URL,
SHA-256, version and retrieval date of each input, and writes the three committed artifacts:
`app/src/main/res/raw/world_cities.tsv`, `app/src/main/res/raw/world_land.bin` and
`scripts/world-clock/world-data.manifest.txt`. Nothing in `./gradlew assembleDebug` or in CI downloads
anything, which keeps the no-network rule of `AGENTS.md` §12 and the offline CI of §18 intact. A JVM test
re-hashes the committed asset and asserts a set of acceptance rows, so the pin and the bytes cannot drift
apart silently. The compressed cost is roughly 110 KB, which is a rounding error beside a single recorded
clip.

**Four inputs, and nothing else is read.** The lock file pins exactly these: Natural Earth's 10m populated
places (`ne_10m_populated_places`, the rows and their names, `NAME_ES`, ISO country, coordinates, population
and `GEONAMESID`); Natural Earth's 110m land (`ne_110m_land`, the map outline and only the outline); GeoNames
`cities5000` (a superset of the Natural Earth rows, so a city GeoNames counts below 15,000 still joins), which supplies the zones and the GeoNames id
space; and the IANA time zone database's `zone.tab` plus `backward`, which supply the per-country validation
and the canonicalisation. A fifth download would be a new decision, not an implementation detail.

**The two committed formats are an interface, so they are fixed here.** `world_cities.tsv` is UTF-8, tab
separated, one header line first, no quoting: tabs and line breaks inside a name are collapsed to single
spaces at write time, so a tab is always a field separator. Its eight columns, in this order, are `name` (the
ASCII name), `name_es` (Natural Earth's `NAME_ES`, falling back to `name` so the Spanish label is never
blank), `iso_a2` (the row's ISO 3166-1 alpha-2 country, the key the `zone.tab` validation uses), `zone_id` (the
canonical IANA zone the row resolved to, never a `backward` link), `latitude` and `longitude` (exactly four
decimals, about 11 m, so a re-run produces the same bytes), `population`, and `geonames_id` (Natural Earth's
`GEONAMESID`, which is also the stable key the world clock list persists). Rows are sorted by population
descending and then by a total order over the remaining columns, so two runs over the same inputs are
byte-identical. A row whose country is unknown cannot be validated against `zone.tab`, so it is dropped and
counted like any other rejected row.

`world_land.bin` keeps the prototype's `UPLD` layout, because it is already written and already read: the four
bytes `UPLD` (version 1 carried by the magic, with no version byte after it), a little-endian `uint32` ring
count, then per ring a little-endian `uint32` point count followed by that many `int16` pairs, longitude then
latitude, each `round(degrees * 100)`. Holes are written as their own rings and the reader fills even-odd, so no
ring carries a hole flag. 1/100 degree keeps every vertex inside `int16` and is finer than a phone can draw at
this map size, and polygons are not clipped: the screen clips them.

**The drop budget is a gate, not a print.** The script exits non-zero when drops from any rule exceed the
pinned budget, and the per-rule fill counts land in `world-data.manifest.txt` so a refresh shows up as a
reviewable diff of numbers rather than a diff of twenty thousand opaque rows. A pipeline that silently drops
a ninth of the planet is worse than one that fails.

**A device's tzdb can be older than the pinned one, so load filters instead of crashing.** The prototype
called `ZoneId.of` inside `mapIndexed` while loading, which turns one unknown id on an old tzdb into an
empty screen. Loading now drops rows whose zone the device cannot resolve and logs the count through
`AlarmLog`; the pinning test proves the build-time side, and the runtime side never crashes a screen. The
bundled ids are canonical main zones, which is the widest-available subset, and that is the whole mitigation.

**Refresh is a reviewed PR, not a schedule.** Natural Earth has not released since 2022-05-13, and GeoNames
changes daily in ways the drop budget would absorb, so a periodic job would mostly rewrite bytes. The data
is refreshed when an upstream release lands or a user reports a mis-zoned city, as one PR that moves the
lock file, the asset and the manifest together. The retrieval date and input hashes make that PR auditable
without trusting the author.

**Attribution is load-bearing, because GeoNames is CC BY 4.0.** The inputs are compatible with this
project's GPL-3.0-or-later under `AGENTS.md` §15: both Natural Earth layers are public domain, the IANA time
zone database is public domain, and GeoNames is CC BY 4.0, which the FSF lists as a free licence compatible
with the GPL. Compatibility is not the same as silence: the licence requires attribution, so it appears in four
places. Settings gains About > Data sources, a short static screen naming "Place data © GeoNames, CC BY
4.0", "Made with Natural Earth" and "Time zone rules: IANA Time Zone Database, public domain", each with its
link. A repo-root `NOTICE` file carries the same text so the duty survives somebody copying the source. A
Data sources section in `docs/site/privacy.md` puts it on the published page, and `README.md` gets one line.

**ICU still labels a zone.** The bundled data decides which places exist and which zone each one belongs to;
`getExemplarLocationName` stays the source of a zone's own label, because it is localized by the platform and
the bundle is not. The two are answers to different questions and the glossary keeps them apart: the
**Gazetteer** is the place list, a zone is what a place resolves to.

## Considered options

- **Platform ICU only, the outcome of issue #7.** Rejected: no bundled bytes and no licence to honour, but it
  cannot name a city that is not a capital or a country, which is the feature.
- **Natural Earth's `TIMEZONE` column as the zone.** Rejected: about 8 percent of rows have none, and the
  ones that have one are not reliably this country's zone. Missing data is repairable; confidently wrong data
  is not.
- **GeoNames `cities15000` as the primary list.** Rejected: zones are complete and current, but Spanish names
  need the 196 MB `alternateNamesV2` pipeline and the asset grows for no gain over the Natural Earth join.
- **GeoNames zones joined with no per-country validation.** Rejected: name plus country matching still
  produces cross-country hits, and the validation is what converts an attribution obligation into a correct
  answer.
- **A Gradle task that downloads the inputs at build time.** Rejected: reproducible from source alone, but it
  puts the network in the build and in CI, and the main input has not changed since 2022, so it would mostly
  re-fetch dead bytes.
- **A scheduled refresh, or a CI job watching upstream.** Rejected: the data is versioned by release, not by
  day, and an automatic update would land an unattributed or mis-joined change nobody reviewed.
- **Shipped ODbL data or Natural Earth's own time-zone polygons.** Rejected: ODbL's share-alike terms do not
  fit a GPL app's expectations here, and the polygons are geometry, not a place-to-zone answer.

## Consequences

`GLOSSARY.md` gains the **Gazetteer** and **Data sources**, so the asset, the licence screen and the ICU
zone label stop being three things one word has to mean. `AGENTS.md` needs no change: the pipeline adds no
permission, no network, no dependency and no runtime failure mode that the invariants do not already cover.

Two implementation slices follow from this: the generator, assets and pinning test, and the world clock
screen that consumes them. The screen is issue #15's design, not this ADR's.

**Amendment, 2026-10-27 (issue #44).** The manifest was first placed at
`app/src/main/res/raw/world-data.manifest.txt`. Android's resource merger rejects a hyphen in a `res/raw` file
name, and lint's `UnusedResources` (a build error here) rejects a raw resource nothing reads, so the manifest
lives beside the lock as `scripts/world-clock/world-data.manifest.txt` and does not ship in the APK. Its format
and content are unchanged.

Two claims are reasoned but unverified. The drop counts above come from the research measurements in issue
#21, not from a run of the generator, so the first real generation may move the numbers and the pinned
budget with them. The ~110 KB figure is the research estimate for the compressed assets; if the build
disagrees, the size changes and the decision does not.
