"""Unit tests for scripts/world-clock/generate.py (ADR 0011).

Stdlib only. Every fixture is built in memory or in a temp directory; nothing touches the network or
the real cache. Run from the repository root:

    python3 -I -m unittest discover -s scripts/world-clock -p 'test_*.py' -v
"""

import contextlib
import io
import json
import os
import struct
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generate  # noqa: E402

# A zone no fixture country lists. Natural Earth's TIMEZONE column is never read, so any row carrying
# this zone would prove the column leaked into resolution.
WRONG_ZONE = "Pacific/Chatham"

# Countries used by most fixtures. NL and IN are single-zone; US, RU and UA are multi-zone.
STANDARD_ZONES = {
    "NL": ["Europe/Amsterdam"],
    "US": ["America/Chicago", "America/New_York", "America/Los_Angeles"],
    "DE": ["Europe/Berlin"],
    "ES": ["Europe/Madrid"],
    "IN": ["Asia/Kolkata"],
    "RU": ["Europe/Moscow", "Asia/Yekaterinburg"],
    "UA": ["Europe/Kyiv", "Europe/Simferopol"],
}


# ------------------------------------------------------------------------------------------- fixtures


def geonames_zip(*records):
    """An in-memory cities5000.zip. Each record is (id, name, country, zone, lat, lon[, alternates])."""
    lines = []
    for record in records:
        record_id, name, country, zone, latitude, longitude, *rest = record
        alternates = rest[0] if rest else ""
        fields = [""] * 19
        fields[0] = str(record_id)
        fields[1] = name
        fields[2] = name
        fields[3] = alternates
        fields[4] = str(latitude)
        fields[5] = str(longitude)
        fields[8] = country
        fields[17] = zone
        lines.append("\t".join(fields))
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("cities5000.txt", "\n".join(lines) + "\n")
    return buffer.getvalue()


def place(name, iso, latitude, longitude, population=20000, geonames_id=-1, name_es=""):
    """One Natural Earth populated-places properties object. TIMEZONE is always the wrong zone."""
    return {
        "NAMEASCII": name,
        "NAME": name,
        "NAME_ES": name_es,
        "ISO_A2": iso,
        "LATITUDE": latitude,
        "LONGITUDE": longitude,
        "POP_MAX": population,
        "GEONAMESID": geonames_id,
        "TIMEZONE": WRONG_ZONE,
    }


def places_geojson(*properties):
    features = [
        {"type": "Feature", "properties": p, "geometry": {"type": "Point", "coordinates": [p["LONGITUDE"], p["LATITUDE"]]}}
        for p in properties
    ]
    return json.dumps({"type": "FeatureCollection", "features": features}).encode("utf-8")


def land_geojson(*geometries):
    features = [{"type": "Feature", "properties": {}, "geometry": geometry} for geometry in geometries]
    return json.dumps({"type": "FeatureCollection", "features": features}).encode("utf-8")


def zone_tab_text(countries):
    lines = ["# fixture zone.tab"]
    for iso, zones in countries.items():
        for zone in zones:
            lines.append("{}\t+0000+00000\t{}".format(iso, zone))
    return "\n".join(lines) + "\n"


def backward_text(lines):
    return "\n".join(lines) + "\n"


def resolver_for(geonames, countries=STANDARD_ZONES, backward=()):
    links = generate.parse_backward(backward_text(backward))
    zone_tab = generate.parse_zone_tab(zone_tab_text(countries), links)
    return generate.Resolver(generate.parse_geonames(geonames), zone_tab, links)


def rows_for(places, geonames, countries=STANDARD_ZONES, backward=()):
    """Run the real parse -> resolve -> build path on fixtures. Returns (rows, stats)."""
    resolver = resolver_for(geonames, countries, backward)
    return generate.build_rows(generate.parse_places(places_geojson(*places)), resolver)


def fake_lock():
    return {
        "files": {
            key: {
                "input": key,
                "url": "https://example.invalid/" + key,
                "sha256": "0" * 64,
                "version": "v-test",
                "retrieved": "2026-01-01",
                "filename": key,
            }
            for key in generate.LOCK_FILES
        }
    }


def world_fixtures():
    """Every pinned input as bytes, small enough to read at a glance."""
    return {
        "ne_populated_places": places_geojson(
            place("Amsterdam", "NL", 52.3676, 4.9041, 800000, geonames_id=1),
            place("Arlington", "US", 32.68, -97.02, 400000),
        ),
        "ne_land": land_geojson(
            {
                "type": "Polygon",
                "coordinates": [[[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]], [[2, 2], [3, 2], [3, 3], [2, 2]]],
            }
        ),
        "geonames_cities5000": geonames_zip(
            (1, "Amsterdam", "NL", "Europe/Amsterdam", 52.3676, 4.9041),
            (101, "Arlington", "US", "America/Chicago", 32.7357, -97.1081),
            (102, "Arlington", "US", "America/New_York", 38.8816, -77.0910),
        ),
        "tzdata_zone_tab": zone_tab_text(STANDARD_ZONES).encode("utf-8"),
        "tzdata_backward": backward_text([]).encode("utf-8"),
    }


def write_lock(directory, files):
    path = os.path.join(directory, "lock.toml")
    with open(path, "w", encoding="utf-8") as handle:
        for key, fields in files.items():
            handle.write("[files.{}]\n".format(key))
            for name, value in fields.items():
                handle.write("{} = {}\n".format(name, json.dumps(value)))
    return path


def lock_entry(input_name):
    return {
        "input": input_name,
        "url": "https://example.invalid/" + input_name,
        "sha256": "0" * 64,
        "version": "v-test",
        "retrieved": "2026-01-01",
        "filename": input_name.replace(" ", "_"),
    }


def by_name(rows):
    return {row[0]: row for row in rows}


# Column positions of a build_rows() row.
NAME, NAME_ES, ISO, ZONE, LATITUDE, LONGITUDE, POPULATION, GEONAMES_ID = range(8)


# ---------------------------------------------------------------------------------------- link parsing


class ParseBackwardTest(unittest.TestCase):
    def test_parse_backward_follows_links_and_rejects_cycles(self):
        """Link lines map name to target, comments are ignored, link chains resolve to the final zone, cycles raise."""
        text = backward_text(
            [
                "# a comment line",
                "Link Europe/Brussels Europe/Amsterdam",
                "Link Europe/Amsterdam Europe/Oldname",
                "Link Europe/Paris Europe/Lyon # inline comment",
                "Zone Europe/Nowhere 0 - LMT",
            ]
        )
        self.assertEqual(
            generate.parse_backward(text),
            {
                "Europe/Amsterdam": "Europe/Brussels",
                "Europe/Oldname": "Europe/Brussels",
                "Europe/Lyon": "Europe/Paris",
            },
        )
        cycle = backward_text(["Link Zone/B Zone/A", "Link Zone/A Zone/B"])
        with self.assertRaises(generate.GenerationError):
            generate.parse_backward(cycle)


class ParseZoneTabTest(unittest.TestCase):
    def test_parse_zone_tab_canonicalises_zone_ids_through_links(self):
        """A zone.tab row naming a backward link is recorded under the link's target."""
        links = generate.parse_backward(backward_text(["Link Europe/Brussels Europe/Amsterdam"]))
        text = "# comment\nNL\t+5223+00454\tEurope/Amsterdam\nBE\t+5050+00420\tEurope/Brussels\n"
        self.assertEqual(generate.parse_zone_tab(text, links), {"NL": {"Europe/Brussels"}, "BE": {"Europe/Brussels"}})


# ------------------------------------------------------------------------------------- zone resolution


class ResolutionRuleTest(unittest.TestCase):
    def test_geonames_id_rule_takes_zone_from_geonames_not_timezone_column(self):
        """A row whose GEONAMESID matches a record in its country takes that record's zone and id."""
        rows, stats = rows_for([place("Amsterdam", "NL", 52.3676, 4.9041, geonames_id=1)], world_fixtures()["geonames_cities5000"])
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0][ZONE], "Europe/Amsterdam")
        self.assertEqual(rows[0][GEONAMES_ID], 1)
        self.assertEqual(stats["filled"]["geonames_id"], 1)

    def test_geonames_id_rule_rejected_by_distance_then_name_rule_recovers(self):
        """An id record more than 75 km away is rejected; the nearest same-name record then supplies the zone, and the NE id stays."""
        geonames = geonames_zip(
            (1, "Amsterdam", "NL", "Europe/Amsterdam", 52.3676, 4.9041),
            (2, "Groningen", "NL", "Europe/Amsterdam", 53.2194, 6.5665),
        )
        rows, stats = rows_for([place("Groningen", "NL", 53.2194, 6.5665, geonames_id=1)], geonames)
        self.assertEqual(stats["rejected"]["geonames_id"], 1)
        self.assertEqual(stats["filled"]["name_country"], 1)
        self.assertEqual(stats["id_from_name"], 0)
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0][GEONAMES_ID], 1)
        self.assertEqual(rows[0][ZONE], "Europe/Amsterdam")

    def test_name_rule_disambiguates_namesakes_by_distance(self):
        """Two Arlingtons in one country: distance, not id order, picks the one near the point. A namesake beyond 30 km never matches."""
        # Ids are ordered 2, 3, 101: an id-ordered pick would choose Virginia, then the 20 km Los Angeles
        # record, and never the nearest one. Both lower-id records lie within 30 km or farther, so only
        # ranking by distance yields Chicago.
        geonames = geonames_zip(
            (2, "Arlington", "US", "America/New_York", 38.8816, -77.0910),
            (3, "Arlington", "US", "America/Los_Angeles", 32.50, -97.02),
            (101, "Arlington", "US", "America/Chicago", 32.7357, -97.1081),
        )
        places = [
            place("Arlington", "US", 32.68, -97.02),
            # Arlington, Oregon: no record within 30 km, so the name rule must not match it at all.
            place("Arlington", "US", 45.7, -120.2),
        ]
        rows, stats = rows_for(places, geonames)
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0][ZONE], "America/Chicago")
        self.assertEqual(rows[0][GEONAMES_ID], 101)
        self.assertEqual(stats["id_from_name"], 1)
        self.assertEqual(stats["dropped"]["unresolved_zone"], 1)

    def test_name_rule_matches_alternate_name_and_name_es(self):
        """Alternate names and NAME_ES both feed the name rule; each row gets the id of the record it matched."""
        geonames = geonames_zip(
            (201, "Munich", "DE", "Europe/Berlin", 48.1374, 11.5755, "Muenchen,Monaco di Baviera"),
            (202, "Sevilla", "ES", "Europe/Madrid", 37.3891, -5.9845),
        )
        places = [
            # Matches only through the GeoNames alternate name "Muenchen".
            place("Muenchen", "DE", 48.1374, 11.5755, 1400000),
            # Matches only through NAME_ES "Sevilla"; the ASCII name "Seville" is not in GeoNames.
            place("Seville", "ES", 37.3891, -5.9845, 700000, name_es="Sevilla"),
        ]
        rows, stats = rows_for(places, geonames)
        named = by_name(rows)
        self.assertEqual(named["Muenchen"][GEONAMES_ID], 201)
        self.assertEqual(named["Seville"][GEONAMES_ID], 202)
        self.assertEqual(stats["filled"]["name_country"], 2)
        self.assertEqual(stats["id_from_name"], 2)

    def test_single_zone_country_fills_zone_but_row_drops_without_stable_id(self):
        """A single-zone country supplies the zone, but with no id from any rule the row is dropped no_stable_id."""
        # An unrelated record keeps the GeoNames file non-empty; it matches nothing in NL.
        rows, stats = rows_for([place("Nowhere", "NL", 52.0, 5.0)], geonames_zip((900, "Elsewhere", "DE", "Europe/Berlin", 50.0, 8.0)))
        self.assertEqual(stats["filled"]["single_zone_country"], 1)
        self.assertEqual(stats["dropped"]["no_stable_id"], 1)
        self.assertEqual(rows, [])

    def test_name_match_with_rejected_zone_still_supplies_the_id(self):
        """The nearby namesake is the same place: its zone is refused, rule 3 fills the zone, its id is kept."""
        rows, stats = rows_for([place("Utrecht", "NL", 52.09, 5.12)], geonames_zip((301, "Utrecht", "NL", "Europe/Brussels", 52.09, 5.12)))
        self.assertEqual(stats["rejected"]["name_country"], 1)
        self.assertEqual(stats["filled"]["single_zone_country"], 1)
        self.assertEqual(rows[0][ZONE], "Europe/Amsterdam")
        self.assertEqual(rows[0][GEONAMES_ID], 301)

    def test_cross_country_zone_is_rejected_and_row_dropped_unresolved(self):
        """A record whose zone belongs to another country's zone.tab entry is rejected; multi-zone RU has no fallback."""
        geonames = geonames_zip((301, "Simferopol", "RU", "Europe/Simferopol", 44.9521, 34.1024))
        rows, stats = rows_for([place("Simferopol", "RU", 44.9521, 34.1024, 330000, geonames_id=301)], geonames)
        self.assertEqual(rows, [])
        self.assertEqual(stats["rejected"]["geonames_id"], 1)
        self.assertEqual(stats["rejected"]["name_country"], 1)
        self.assertEqual(stats["dropped"]["unresolved_zone"], 1)

    def test_unknown_country_is_dropped(self):
        """A row whose ISO_A2 is -99 (Natural Earth's placeholder) is dropped unknown_country."""
        rows, stats = rows_for([place("Disputed", "-99", 0.0, 0.0, geonames_id=1)], geonames_zip((1, "Disputed", "NL", "Europe/Amsterdam", 0.0, 0.0)))
        self.assertEqual(rows, [])
        self.assertEqual(stats["dropped"]["unknown_country"], 1)

    def test_population_floor_excludes_places_below_15000(self):
        """A place at 14999 never becomes a candidate, even when a GeoNames record would resolve it; 15000 is included."""
        geonames = geonames_zip((1, "Smallville", "NL", "Europe/Amsterdam", 52.0, 5.0), (2, "Bigtown", "NL", "Europe/Amsterdam", 52.0, 5.0))
        places = [
            place("Smallville", "NL", 52.0, 5.0, 14999, geonames_id=1),
            place("Bigtown", "NL", 52.0, 5.0, 15000, geonames_id=2),
        ]
        rows, stats = rows_for(places, geonames)
        self.assertEqual(stats["places"], 1)
        self.assertEqual([row[NAME] for row in rows], ["Bigtown"])

    def test_duplicate_id_keeps_the_higher_population_row(self):
        """Two places resolving to one GeoNames id: the higher-population row is kept and duplicate_id counts the other."""
        geonames = geonames_zip((7, "Hub", "NL", "Europe/Amsterdam", 52.0, 5.0))
        places = [
            place("Beta", "NL", 52.0, 5.0, 20000, geonames_id=7),
            place("Alpha", "NL", 52.0, 5.0, 50000, geonames_id=7),
        ]
        rows, stats = rows_for(places, geonames)
        self.assertEqual([row[NAME] for row in rows], ["Alpha"])
        self.assertEqual(stats["dropped"]["duplicate_id"], 1)
        self.assertEqual(stats["written"], 1)

    def test_backward_link_zone_is_written_as_its_target_and_counted(self):
        """A GeoNames zone that is a backward link is written as the link target and bumps the canonicalised counter."""
        geonames = geonames_zip((401, "Kolkata", "IN", "Asia/Calcutta", 22.5697, 88.3697))
        rows, stats = rows_for(
            [place("Kolkata", "IN", 22.5697, 88.3697, 4500000, geonames_id=401)],
            geonames,
            backward=["Link Asia/Kolkata Asia/Calcutta"],
        )
        self.assertEqual(rows[0][ZONE], "Asia/Kolkata")
        self.assertEqual(stats["canonicalised"], 1)


# ------------------------------------------------------------------------------------------- encoding


class EncodeCitiesTest(unittest.TestCase):
    def test_encode_cities_header_precision_squashing_and_sort_order(self):
        """Eight ADR columns, four-decimal coordinates, tabs in names squashed, population descending then name."""
        geonames = geonames_zip(*[(i, "x", "NL", "Europe/Amsterdam", 52.0, 5.0) for i in range(11, 15)])
        places = [
            place("Zeta", "NL", 52.0, 5.12, 20000, geonames_id=11),
            place("Alpha", "NL", 52.0, 5.12, 20000, geonames_id=12),
            place("Tab\there\nTown", "NL", 52.0, 5.12, 30000, geonames_id=13),
            place("Big", "NL", 52.0, 5.12, 50000, geonames_id=14),
        ]
        rows, _ = rows_for(places, geonames)
        text = generate.encode_cities(rows).decode("utf-8")
        lines = text.split("\n")
        self.assertEqual(lines[0], "name\tname_es\tiso_a2\tzone_id\tlatitude\tlongitude\tpopulation\tgeonames_id")
        data = [line.split("\t") for line in lines[1:] if line]
        self.assertEqual([fields[0] for fields in data], ["Big", "Tab here Town", "Alpha", "Zeta"])
        self.assertEqual(data[0], ["Big", "Big", "NL", "Europe/Amsterdam", "52.0000", "5.1200", "50000", "14"])
        self.assertEqual(len(data[1]), 8)
        self.assertNotIn("\r", text)
        self.assertTrue(text.endswith("\n"))


class EncodeLandTest(unittest.TestCase):
    def test_encode_land_layout_holes_and_range(self):
        """Magic, ring and point counts, int16 1/100-degree pairs, holes as their own rings, out-of-range raises."""
        geometries = [
            {
                "type": "Polygon",
                "coordinates": [[[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]], [[2, 2], [3, 2], [3, 3], [2, 2]]],
            },
            {
                "type": "MultiPolygon",
                "coordinates": [
                    [[[-120.5, 45.25], [-120.0, 45.25], [-120.0, 46.0], [-120.5, 45.25]]],
                    [[[100, -10], [101, -10], [101, -9], [100, -10]]],
                ],
            },
        ]
        rings = generate.parse_land(land_geojson(*geometries))
        data = generate.encode_land(rings)
        self.assertEqual(data[:4], b"UPLD")

        offset = 4
        (ring_count,) = struct.unpack_from("<I", data, offset)
        offset += 4
        decoded = []
        for _ in range(ring_count):
            (count,) = struct.unpack_from("<I", data, offset)
            offset += 4
            ring = []
            for _ in range(count):
                ring.append(struct.unpack_from("<hh", data, offset))
                offset += 4
            decoded.append(ring)
        self.assertEqual(offset, len(data))

        self.assertEqual(ring_count, 4)
        self.assertEqual(decoded[0], [(0, 0), (1000, 0), (1000, 1000), (0, 1000), (0, 0)])
        # The hole is ring 1, not merged into the outer ring.
        self.assertEqual(decoded[1], [(200, 200), (300, 200), (300, 300), (200, 200)])
        self.assertEqual(decoded[2], [(-12050, 4525), (-12000, 4525), (-12000, 4600), (-12050, 4525)])
        self.assertEqual(decoded[3], [(10000, -1000), (10100, -1000), (10100, -900), (10000, -1000)])

        with self.assertRaises(generate.GenerationError):
            generate.encode_land([[(400.0, 0.0)]])
        boundary = generate.encode_land([[(327.67, -327.68)]])
        self.assertEqual(struct.unpack_from("<hh", boundary, 12), (32767, -32768))


# ------------------------------------------------------------------------------------ determinism


class DeterminismTest(unittest.TestCase):
    def test_generate_twice_gives_identical_bytes(self):
        """The same pinned inputs produce byte-identical outputs on every run."""
        fixtures = world_fixtures()
        lock = fake_lock()
        first, first_stats = generate.generate(lock, fixtures.__getitem__, generate.DEFAULT_BUDGET)
        second, second_stats = generate.generate(lock, fixtures.__getitem__, generate.DEFAULT_BUDGET)
        self.assertEqual(set(first), {"world_cities.tsv", "world_land.bin", generate.MANIFEST_NAME})
        self.assertEqual(first, second)
        self.assertEqual(first_stats, second_stats)


# --------------------------------------------------------------------------------------- budgets


class BudgetTest(unittest.TestCase):
    def test_check_budget_names_the_reason_and_passes_at_equality(self):
        """A drop count above its budget raises naming the reason; equal to the budget passes."""
        stats = {"dropped": {"unknown_country": 3, "unresolved_zone": 0, "no_stable_id": 0, "duplicate_id": 0}}
        budget = dict(generate.DEFAULT_BUDGET)
        budget["unknown_country"] = 3
        generate.check_budget(stats, budget)
        stats["dropped"]["unknown_country"] = 4
        with self.assertRaises(generate.GenerationError) as raised:
            generate.check_budget(stats, budget)
        self.assertIn("unknown_country=4 (budget 3)", str(raised.exception))

    def test_parse_budget_rejects_unknown_reason(self):
        """--budget accepts reason=count for the four known reasons only, and overrides just that reason."""
        self.assertEqual(generate.parse_budget(None), generate.DEFAULT_BUDGET)
        overridden = generate.parse_budget(["unknown_country=7"])
        self.assertEqual(overridden["unknown_country"], 7)
        self.assertEqual(overridden["duplicate_id"], generate.DEFAULT_BUDGET["duplicate_id"])
        for bad in ["bogus=1", "unknown_country=x", "unknown_country"]:
            with self.subTest(value=bad):
                with self.assertRaises(generate.GenerationError):
                    generate.parse_budget([bad])


# ------------------------------------------------------------------------------------------ the lock


class LoadLockTest(unittest.TestCase):
    def test_real_lock_passes(self):
        """The committed data-sources.lock pins exactly the five file keys across four inputs."""
        lock = generate.load_lock(generate.LOCK_PATH)
        self.assertEqual(set(lock["files"]), set(generate.LOCK_FILES))

    def test_lock_missing_a_key_or_with_a_fifth_input_raises(self):
        """A lock with one key missing, or a fifth distinct input, is refused before any input is read."""
        # Four inputs: the two tzdata files share one input name, as the committed lock does.
        four_inputs = {
            "ne_populated_places": lock_entry("Natural Earth 10m populated places"),
            "ne_land": lock_entry("Natural Earth 110m land"),
            "geonames_cities5000": lock_entry("GeoNames cities5000"),
            "tzdata_zone_tab": lock_entry("IANA time zone database"),
            "tzdata_backward": lock_entry("IANA time zone database"),
        }
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(set(generate.load_lock(write_lock(directory, four_inputs))["files"]), set(generate.LOCK_FILES))

            missing = dict(four_inputs)
            del missing["tzdata_backward"]
            with self.assertRaises(generate.GenerationError):
                generate.load_lock(write_lock(directory, missing))

            # Five keys, five distinct input names: the lock must describe exactly four inputs.
            fifth = dict(four_inputs)
            fifth["tzdata_backward"] = lock_entry("Extra input")
            with self.assertRaises(generate.GenerationError) as raised:
                generate.load_lock(write_lock(directory, fifth))
            self.assertIn("four inputs", str(raised.exception))


# ------------------------------------------------------------------------------------- input reads


class ReadInputTest(unittest.TestCase):
    def test_read_input_refuses_a_sha256_mismatch(self):
        """A cached input whose bytes do not hash to the lock's sha256 is refused; the matching bytes are returned."""
        payload = b"pinned bytes"
        with tempfile.TemporaryDirectory() as directory:
            with open(os.path.join(directory, "ne_land.geojson"), "wb") as handle:
                handle.write(b"tampered bytes")
            lock = {"files": {"ne_land": dict(lock_entry("Natural Earth 110m land"), filename="ne_land.geojson", sha256=generate.sha256_of(payload))}}
            with self.assertRaises(generate.GenerationError) as raised:
                generate.read_input(lock, "ne_land", directory, fetch=False)
            self.assertIn("does not match", str(raised.exception))

            with open(os.path.join(directory, "ne_land.geojson"), "wb") as handle:
                handle.write(payload)
            self.assertEqual(generate.read_input(lock, "ne_land", directory, fetch=False), payload)


# ---------------------------------------------------------------------------------------- main()


class MainExitCodeTest(unittest.TestCase):
    def test_main_returns_1_when_an_input_is_missing_and_writes_nothing(self):
        """An empty input directory makes main() return 1 and creates no output directory."""
        with tempfile.TemporaryDirectory() as inputs, tempfile.TemporaryDirectory() as scratch:
            out = os.path.join(scratch, "out")
            stderr = io.StringIO()
            with contextlib.redirect_stderr(stderr):
                code = generate.main(["generate.py", "--inputs", inputs, "--out", out, "--lock", generate.LOCK_PATH])
            self.assertEqual(code, 1)
            self.assertIn("missing input", stderr.getvalue())
            self.assertFalse(os.path.exists(out))


if __name__ == "__main__":
    unittest.main()
