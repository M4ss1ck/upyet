#!/usr/bin/env python3
"""World clock data generator: four pinned inputs -> the three committed assets (ADR 0011).

This script is manual. No Gradle task and no CI job runs it, and nothing in the build downloads anything
(AGENTS.md §12, §18). A data refresh is one reviewed PR that moves `data-sources.lock`, the assets and the
manifest together.

Usage, from the repository root:

    python3 -I scripts/world-clock/generate.py --fetch     # download the pinned inputs, verify, generate
    python3 -I scripts/world-clock/generate.py             # generate from inputs already in the cache

The cache directory defaults to `$XDG_CACHE_HOME/upyet/world-clock` (`~/.cache/...`) and is set with
`--inputs DIR`. Every input is checked against the SHA-256 in the lock before it is read, so a stale or
tampered file stops the run. GeoNames publishes `cities5000` as a daily dump with no archive, so `--fetch`
of an old lock fails its hash once upstream moves: that is the signal to refresh the lock, not a bug.

Exactly four inputs are read, and nothing else:
  * Natural Earth 10m populated places: names, NAME_ES, ISO_A2, coordinates, POP_MAX, GEONAMESID.
    Its TIMEZONE column is never read (ADR 0011: about 8 percent empty, some rows carry another
    country's zone).
  * Natural Earth 110m land: the map outline.
  * GeoNames cities5000: the zone of each place, and the GeoNames id space.
  * IANA tzdata `zone.tab` plus `backward`: per-country validation and link canonicalisation.

Zone resolution for each place, in order; the first rule whose zone passes validation wins:
  1. `geonames_id`: the GeoNames record with the row's GEONAMESID.
  2. `name_country`: the nearest GeoNames record in the row's country whose name, ASCII name or an
     alternate name equals the row's name or NAME_ES (case-insensitively), within MAX_NAME_MATCH_KM of
     the row's point. The distance is what tells Arlington, Texas from Arlington, Virginia.
  3. `single_zone_country`: the zone of a country `zone.tab` lists exactly one canonical zone for.
Rule 1 also refuses a record farther than MAX_ID_MATCH_KM from the row's point. A candidate is canonicalised through `backward` and accepted only when it is one of the canonical zones
`zone.tab` lists for the row's own country. A rejected candidate falls through to the next rule, so a
GEONAMESID that points at a namesake abroad (Melbourne, US for Melbourne, AU) is counted and then
recovered by name. Rows that no rule resolves are dropped and counted per reason; `--budget` caps each
reason and the run exits non-zero when one is exceeded.

Outputs, the formats ADR 0011 fixes because slice #45 reads them:

  world_cities.tsv   UTF-8, tab separated, one header line, no quoting. Columns in order: name, name_es,
                     iso_a2, zone_id, latitude, longitude, population, geonames_id. Coordinates carry
                     exactly four decimals. Sorted by population descending, then name, iso_a2, latitude,
                     longitude, geonames_id: a total order, so the same inputs give the same bytes.
                     geonames_id is Natural Earth's GEONAMESID; where Natural Earth has none (-1, Sydney
                     among them) it is the id of the GeoNames record the name rule matched. It is the key
                     the world clock list persists, so a row without a positive id is dropped and a
                     repeated id keeps only its first row in output order.
  world_land.bin     b"UPLD", uint32 ring count, then per ring a uint32 point count and that many
                     (int16 lon, int16 lat) pairs at 1/100 degree, all little endian. Holes are their own
                     rings, filled even-odd by the reader; nothing is clipped.
  scripts/world-clock/world-data.manifest.txt
                     The inputs (version, retrieval date, SHA-256), the per-rule fill and drop counts and
                     the SHA-256 of both assets, so a refresh reviews as a diff of numbers.
"""

import argparse
import hashlib
import io
import json
import math
import os
import struct
import sys
import tomllib
import urllib.request
import zipfile

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))
LOCK_PATH = os.path.join(SCRIPT_DIR, "data-sources.lock")
OUTPUT_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "res", "raw")
# Next to the lock, not in res/raw: it is for reviewers, has no runtime reader, and should not ship in the APK.
MANIFEST_PATH = os.path.join(SCRIPT_DIR, "world-data.manifest.txt")
MANIFEST_NAME = "world-data.manifest.txt"

# POP_MAX below this is a village; ADR 0011's measurements are taken at this floor.
MIN_POPULATION = 15000

# A name match farther than this from the Natural Earth point is a namesake, not the same place.
MAX_NAME_MATCH_KM = 30.0

# Natural Earth's own GEONAMESID gets more slack than a name match, since both points sit somewhere in
# the same metro area, but a record farther than this is a different place carrying a wrong id.
MAX_ID_MATCH_KM = 75.0

# Marks a candidate that a rule found and then refused, so it is counted as rejected rather than absent.
REJECTED = object()

# Hundredths of a degree: the quantisation step of world_land.bin.
DEGREE_SCALE = 100

CITIES_HEADER = ("name", "name_es", "iso_a2", "zone_id", "latitude", "longitude", "population", "geonames_id")

RULES = ("geonames_id", "name_country", "single_zone_country")

# Drop reasons, each with its own budget. A refresh that exceeds one fails instead of shipping a gutted list.
DROP_REASONS = ("unknown_country", "unresolved_zone", "no_stable_id", "duplicate_id")
DEFAULT_BUDGET = {
    "unknown_country": 20,
    "unresolved_zone": 80,
    "no_stable_id": 25,
    "duplicate_id": 10,
}

# The lock's file keys, in the order the four inputs are named; tzdata is one input of two files.
LOCK_FILES = ("ne_populated_places", "ne_land", "geonames_cities5000", "tzdata_zone_tab", "tzdata_backward")


class GenerationError(Exception):
    """An expected failure: bad lock, missing or mismatched input, or a blown budget."""


# ----------------------------------------------------------------------------------------------- inputs


def load_lock(path):
    with open(path, "rb") as handle:
        lock = tomllib.load(handle)
    files = lock.get("files", {})
    if tuple(sorted(files)) != tuple(sorted(LOCK_FILES)):
        raise GenerationError("lock must pin exactly {}, found {}".format(", ".join(LOCK_FILES), ", ".join(sorted(files))))
    for key in LOCK_FILES:
        entry = files[key]
        for field in ("input", "url", "sha256", "version", "retrieved", "filename"):
            if not entry.get(field):
                raise GenerationError("lock entry {} is missing {}".format(key, field))
    inputs = {entry["input"] for entry in files.values()}
    if len(inputs) != 4:
        raise GenerationError("lock must describe exactly four inputs, found {}".format(len(inputs)))
    return lock


def sha256_of(payload):
    return hashlib.sha256(payload).hexdigest()


def read_input(lock, key, inputs_dir, fetch):
    """The pinned bytes of one lock entry, downloading them first when asked; the hash always decides."""
    entry = lock["files"][key]
    path = os.path.join(inputs_dir, entry["filename"])
    if fetch:
        os.makedirs(inputs_dir, exist_ok=True)
        with urllib.request.urlopen(entry["url"], timeout=120) as response:
            write_atomically(path, response.read())
    if not os.path.exists(path):
        raise GenerationError("missing input {} (run with --fetch, or pass --inputs)".format(path))
    with open(path, "rb") as handle:
        payload = handle.read()
    actual = sha256_of(payload)
    if actual != entry["sha256"]:
        raise GenerationError("{} hash {} does not match the lock's {}".format(entry["filename"], actual, entry["sha256"]))
    return payload


def parse_backward(text):
    """Link name -> target, followed to the end so a link to a link still lands on a zone."""
    links = {}
    for line in text.splitlines():
        fields = line.split("#", 1)[0].split()
        if len(fields) >= 3 and fields[0] == "Link":
            links[fields[2]] = fields[1]

    def resolve(name):
        seen = set()
        while name in links:
            if name in seen:
                raise GenerationError("backward link cycle at {}".format(name))
            seen.add(name)
            name = links[name]
        return name

    return {name: resolve(name) for name in links}


def parse_zone_tab(text, links):
    """ISO alpha-2 -> the set of canonical zones zone.tab lists for that country."""
    zones = {}
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) < 3:
            raise GenerationError("malformed zone.tab line: {!r}".format(line))
        zones.setdefault(fields[0], set()).add(links.get(fields[2], fields[2]))
    return zones


def parse_geonames(zip_bytes):
    """GeoNames records as (id, lowercase names, country, zone, latitude, longitude) from cities5000.

    The names are the record's name, ASCII name and alternate names, so a Natural Earth spelling that
    GeoNames only knows as an alternate ("Jaboatao" for "Jaboatão dos Guararapes") still matches.
    """
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as archive:
        names = archive.namelist()
        if names != ["cities5000.txt"]:
            raise GenerationError("unexpected cities5000.zip contents: {}".format(names))
        text = archive.read("cities5000.txt").decode("utf-8")
    records = []
    for line in text.splitlines():
        fields = line.split("\t")
        if len(fields) < 18:
            raise GenerationError("malformed GeoNames line: {!r}".format(line[:80]))
        names = {fields[1], fields[2], *fields[3].split(",")}
        names = frozenset(squashed(name).lower() for name in names if squashed(name))
        records.append((int(fields[0]), names, fields[8], fields[17], float(fields[4]), float(fields[5])))
    return records


def squashed(value):
    """Tab- and newline-free single-line form of a text field, so the TSV needs no quoting."""
    return " ".join(str(value or "").split())


def parse_places(geojson_bytes):
    """Natural Earth rows at or above the population floor. TIMEZONE is deliberately not read."""
    document = json.loads(geojson_bytes.decode("utf-8"))
    places = []
    for feature in document["features"]:
        properties = feature["properties"]
        population = int(properties.get("POP_MAX") or 0)
        if population < MIN_POPULATION:
            continue
        name = squashed(properties.get("NAMEASCII")) or squashed(properties.get("NAME"))
        if not name:
            continue
        places.append(
            {
                "name": name,
                "name_es": squashed(properties.get("NAME_ES")) or name,
                "iso_a2": squashed(properties.get("ISO_A2")),
                "latitude": float(properties["LATITUDE"]),
                "longitude": float(properties["LONGITUDE"]),
                "population": population,
                "geonames_id": int(properties.get("GEONAMESID") or -1),
            }
        )
    return places


def parse_land(geojson_bytes):
    """Every ring of every polygon in file order; holes stay rings of their own."""
    document = json.loads(geojson_bytes.decode("utf-8"))
    rings = []
    for feature in document["features"]:
        geometry = feature.get("geometry")
        if not geometry:
            continue
        polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
        for polygon in polygons:
            rings.extend(polygon)
    return rings


# ------------------------------------------------------------------------------------------- resolution


def distance_km(lat1, lon1, lat2, lon2):
    """Great-circle distance on a 6371 km sphere; plenty for telling a city from its namesake."""
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((phi2 - phi1) / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2
    return 2 * 6371.0 * math.asin(math.sqrt(a))


class Resolver:
    def __init__(self, geonames, zone_tab, links):
        self.links = links
        self.zone_tab = zone_tab
        self.by_id = {record[0]: record for record in geonames}
        self.by_name = {}
        for record in geonames:
            for name in record[1]:
                self.by_name.setdefault((name, record[2]), []).append(record)
        self.single_zone = {country: next(iter(zones)) for country, zones in zone_tab.items() if len(zones) == 1}

    def canonical(self, zone):
        return self.links.get(zone, zone)

    def candidates(self, place):
        """(rule, zone, matched GeoNames id or None) in rule order; zone None means the rule had no answer."""
        record = self.by_id.get(place["geonames_id"]) if place["geonames_id"] > 0 else None
        if record and distance_km(place["latitude"], place["longitude"], record[4], record[5]) > MAX_ID_MATCH_KM:
            # Natural Earth's id names some other place: treat it as a rejected candidate, never a zone.
            yield "geonames_id", REJECTED, None
        else:
            yield "geonames_id", (record[3] if record else None), (record[0] if record else None)

        nearest = self.nearest_namesake(place)
        yield "name_country", (nearest[3] if nearest else None), (nearest[0] if nearest else None)

        yield "single_zone_country", self.single_zone.get(place["iso_a2"]), None

    def nearest_namesake(self, place):
        """The closest same-name record in the row's country within MAX_NAME_MATCH_KM, ties to the lower id."""
        keys = {place["name"].lower(), place["name_es"].lower()}
        matches = {record for key in keys for record in self.by_name.get((key, place["iso_a2"]), [])}
        scored = [(distance_km(place["latitude"], place["longitude"], record[4], record[5]), record[0], record) for record in matches]
        scored = [entry for entry in scored if entry[0] <= MAX_NAME_MATCH_KM]
        return min(scored)[2] if scored else None

    def resolve(self, place, stats):
        """The accepted (zone, geonames id) for one place, or the drop reason as a string."""
        allowed = self.zone_tab.get(place["iso_a2"])
        if not allowed:
            return "unknown_country"
        name_match_id = None
        for rule, zone, matched_id in self.candidates(place):
            if rule == "name_country" and matched_id is not None:
                # Taken even when this record's zone is then rejected: same name, same country and within
                # MAX_NAME_MATCH_KM is the same place, so the id is right even where GeoNames' zone is not.
                name_match_id = matched_id
            if zone is None:
                continue
            if zone is REJECTED:
                stats["rejected"][rule] += 1
                continue
            canonical = self.canonical(zone)
            if canonical not in allowed:
                stats["rejected"][rule] += 1
                continue
            stats["filled"][rule] += 1
            if canonical != zone:
                stats["canonicalised"] += 1
            stable_id = place["geonames_id"] if place["geonames_id"] > 0 else name_match_id
            if not stable_id:
                return "no_stable_id"
            if place["geonames_id"] <= 0:
                stats["id_from_name"] += 1
            return canonical, stable_id
        return "unresolved_zone"


def build_rows(places, resolver):
    stats = {
        "places": len(places),
        "filled": {rule: 0 for rule in RULES},
        "rejected": {rule: 0 for rule in RULES},
        "dropped": {reason: 0 for reason in DROP_REASONS},
        "canonicalised": 0,
        "id_from_name": 0,
    }
    rows = []
    for place in places:
        outcome = resolver.resolve(place, stats)
        if isinstance(outcome, str):
            stats["dropped"][outcome] += 1
            continue
        zone, stable_id = outcome
        rows.append(
            (
                place["name"],
                place["name_es"],
                place["iso_a2"],
                zone,
                "{:.4f}".format(place["latitude"]),
                "{:.4f}".format(place["longitude"]),
                place["population"],
                stable_id,
            )
        )
    rows.sort(key=lambda row: (-row[6], row[0], row[2], float(row[4]), float(row[5]), row[7]))
    unique = []
    seen = set()
    for row in rows:
        if row[7] in seen:
            stats["dropped"]["duplicate_id"] += 1
            continue
        seen.add(row[7])
        unique.append(row)
    stats["written"] = len(unique)
    return unique, stats


# ------------------------------------------------------------------------------------------------ output


def encode_cities(rows):
    lines = ["\t".join(CITIES_HEADER)]
    lines.extend("\t".join(str(field) for field in row) for row in rows)
    return ("\n".join(lines) + "\n").encode("utf-8")


def quantise(value):
    scaled = int(round(value * DEGREE_SCALE))
    if not -32768 <= scaled <= 32767:
        raise GenerationError("coordinate {!r} does not fit int16 at 1/100 degree".format(value))
    return scaled


def encode_land(rings):
    chunks = [b"UPLD", struct.pack("<I", len(rings))]
    for ring in rings:
        chunks.append(struct.pack("<I", len(ring)))
        for point in ring:
            chunks.append(struct.pack("<hh", quantise(point[0]), quantise(point[1])))
    return b"".join(chunks)


def encode_manifest(lock, stats, cities, land, ring_count, point_count):
    lines = [
        "# Generated by scripts/world-clock/generate.py from scripts/world-clock/data-sources.lock. Do not edit.",
        "# ADR 0011. A refresh moves the lock, both assets and this file in one reviewed PR.",
        "",
        "[inputs]",
    ]
    for key in LOCK_FILES:
        entry = lock["files"][key]
        lines.append("{} version={} retrieved={} sha256={}".format(key, entry["version"], entry["retrieved"], entry["sha256"]))
    lines += ["", "[places]", "min_population={}".format(MIN_POPULATION), "candidates={}".format(stats["places"])]
    lines += ["filled.{}={}".format(rule, stats["filled"][rule]) for rule in RULES]
    lines += ["rejected.{}={}".format(rule, stats["rejected"][rule]) for rule in RULES]
    lines += ["dropped.{}={}".format(reason, stats["dropped"][reason]) for reason in DROP_REASONS]
    lines += [
        "canonicalised_links={}".format(stats["canonicalised"]),
        "geonames_id_from_name={}".format(stats["id_from_name"]),
        "written={}".format(stats["written"]),
        "",
        "[land]",
        "rings={}".format(ring_count),
        "points={}".format(point_count),
        "",
        "[outputs]",
        "world_cities.tsv bytes={} sha256={}".format(len(cities), sha256_of(cities)),
        "world_land.bin bytes={} sha256={}".format(len(land), sha256_of(land)),
    ]
    return ("\n".join(lines) + "\n").encode("utf-8")


def check_budget(stats, budget):
    over = [
        "{}={} (budget {})".format(reason, stats["dropped"][reason], budget[reason])
        for reason in DROP_REASONS
        if stats["dropped"][reason] > budget[reason]
    ]
    if over:
        raise GenerationError("drop budget exceeded: " + ", ".join(over))


def write_atomically(path, payload):
    temporary = path + ".tmp"
    with open(temporary, "wb") as handle:
        handle.write(payload)
    os.replace(temporary, path)


def generate(lock, read, budget):
    """Pure core: lock + a reader of pinned bytes -> the three payloads. Writes nothing."""
    links = parse_backward(read("tzdata_backward").decode("utf-8"))
    zone_tab = parse_zone_tab(read("tzdata_zone_tab").decode("utf-8"), links)
    resolver = Resolver(parse_geonames(read("geonames_cities5000")), zone_tab, links)
    rows, stats = build_rows(parse_places(read("ne_populated_places")), resolver)
    check_budget(stats, budget)
    rings = parse_land(read("ne_land"))
    cities = encode_cities(rows)
    land = encode_land(rings)
    manifest = encode_manifest(lock, stats, cities, land, len(rings), sum(len(ring) for ring in rings))
    return {"world_cities.tsv": cities, "world_land.bin": land, MANIFEST_NAME: manifest}, stats


def parse_budget(values):
    budget = dict(DEFAULT_BUDGET)
    for value in values or []:
        reason, _, limit = value.partition("=")
        if reason not in budget or not limit.isdigit():
            raise GenerationError("--budget takes reason=count with reason in {}".format(", ".join(DROP_REASONS)))
        budget[reason] = int(limit)
    return budget


def main(argv):
    parser = argparse.ArgumentParser(description="Generate the world clock assets from the pinned inputs (ADR 0011).")
    cache_home = os.environ.get("XDG_CACHE_HOME") or os.path.join(os.path.expanduser("~"), ".cache")
    parser.add_argument("--inputs", default=os.path.join(cache_home, "upyet", "world-clock"), help="directory holding the inputs")
    parser.add_argument("--fetch", action="store_true", help="download every pinned input into --inputs first")
    parser.add_argument("--lock", default=LOCK_PATH)
    parser.add_argument("--out", default=OUTPUT_DIR, help="directory for the two assets")
    parser.add_argument("--manifest", default=MANIFEST_PATH, help="path of the manifest")
    parser.add_argument("--budget", action="append", metavar="REASON=COUNT", help="override one drop budget")
    args = parser.parse_args(argv[1:])
    try:
        lock = load_lock(args.lock)
        budget = parse_budget(args.budget)
        outputs, stats = generate(lock, lambda key: read_input(lock, key, args.inputs, args.fetch), budget)
    except GenerationError as error:
        sys.stderr.write("generate.py: {}\n".format(error))
        return 1
    os.makedirs(args.out, exist_ok=True)
    for name, payload in outputs.items():
        path = args.manifest if name == MANIFEST_NAME else os.path.join(args.out, name)
        write_atomically(path, payload)
    print("written {} of {} places; filled {}; dropped {}".format(stats["written"], stats["places"], stats["filled"], stats["dropped"]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
