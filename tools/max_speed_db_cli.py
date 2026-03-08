#!/usr/bin/env python3

import datetime
import subprocess
import osmium
import sqlite3
import re
from pathlib import Path
import requests
import argparse
import sys

# ---------- CONFIG ----------
BATCH_SIZE = 5000
POINT_EPSILON = 0.0003

CONTINENT_IDS = [
    "africa",
    "antarctica",
    "asia",
    "australia-oceania",
    "central-america",
    "europe",
    "north-america",
    "south-america",
]

CONTINENTS = {c: [] for c in CONTINENT_IDS}
ISO_CODES = {}

# ---------- FETCH COUNTRIES ----------
def fetch_countries_for_continent(continent):
    url = "https://download.geofabrik.de/index-v1-nogeom.json"
    headers = {"User-Agent": "Mozilla/5.0 (compatible; OSM-SpeedDB/1.0)"}

    r = requests.get(url, headers=headers, timeout=20)
    r.raise_for_status()
    data = r.json()

    countries = []
    iso_map = {}

    for feature in data["features"]:
        props = feature.get("properties", {})
        fid = props.get("id")
        urls = props.get("urls", {})
        pbf_url = urls.get("pbf")
        iso = props.get("iso3166-1:alpha2")

        if not fid or not pbf_url:
            continue

        expected = f"/{continent}/{fid}-latest.osm.pbf"
        if expected not in pbf_url:
            continue

        if not iso:
            continue

        if isinstance(iso, list):
            iso = iso[0]

        countries.append(fid)
        iso_map[fid] = iso.lower()

    countries.sort()
    return countries, iso_map


# ---------- UTILITY ----------
def parse_maxspeed(maxspeed_str):
    if not maxspeed_str:
        return None
    match = re.search(r'\d+', maxspeed_str)
    if match:
        return int(match.group(0))
    return None


# ---------- SPEED HANDLER ----------
class SpeedHandler(osmium.SimpleHandler):
    def __init__(self, cursor):
        super().__init__()
        self.cur = cursor
        self.allowed = {
            "motorway","motorway_link",
            "trunk","trunk_link",
            "primary","primary_link",
            "secondary","secondary_link",
            "tertiary","tertiary_link",
            "unclassified",
            "residential",
            "living_street",
            "service",
        }
        self.speed_rows = []
        self.index_rows = []

    def flush(self):
        if not self.speed_rows:
            return
        self.cur.executemany(
            "INSERT OR REPLACE INTO speed_data VALUES (?,?)",
            self.speed_rows
        )
        self.cur.executemany(
            "INSERT OR REPLACE INTO speed_index VALUES (?,?,?,?,?)",
            self.index_rows
        )
        self.speed_rows.clear()
        self.index_rows.clear()

    def way(self, w):
        tags = w.tags
        highway = tags.get("highway")
        if highway not in self.allowed:
            return
        maxspeed = tags.get("maxspeed")
        if not maxspeed:
            return
        speed = parse_maxspeed(maxspeed)
        if speed is None:
            return

        first = True
        for n in w.nodes:
            lat, lon = n.lat, n.lon
            if first:
                minLat = maxLat = lat
                minLon = maxLon = lon
                first = False
            else:
                minLat = min(minLat, lat)
                maxLat = max(maxLat, lat)
                minLon = min(minLon, lon)
                maxLon = max(maxLon, lon)

        if first:
            return

        eps = POINT_EPSILON
        minLat -= eps
        maxLat += eps
        minLon -= eps
        maxLon += eps

        wid = int(w.id)
        self.speed_rows.append((wid, speed))
        self.index_rows.append((wid, minLat, maxLat, minLon, maxLon))

        if len(self.speed_rows) >= BATCH_SIZE:
            self.flush()


# ---------- DOWNLOAD ----------
def download_osm(country: str, continent: str):
    url = f"https://download.geofabrik.de/{continent}/{country}-latest.osm.pbf"
    local_file = Path(f"{country}.osm.pbf")
    print(f"Downloading {url} ...")

    headers = {"User-Agent": "Mozilla/5.0"}
    r = requests.get(url, headers=headers, stream=True)
    r.raise_for_status()
    total = int(r.headers.get("content-length", 0))
    downloaded = 0

    with open(local_file, "wb") as f:
        for chunk in r.iter_content(chunk_size=8192):
            if not chunk:
                continue
            f.write(chunk)
            downloaded += len(chunk)
            if total > 0:
                percent = int(downloaded / total * 50)
                sys.stdout.write(f"\rDownloading: {percent}%")
                sys.stdout.flush()
    print(f"\nSaved to {local_file}")
    return local_file.resolve()


# ---------- PASS 1 & 2 FILTER ----------
class WayCollector(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.allowed = {
            "motorway","motorway_link",
            "trunk","trunk_link",
            "primary","primary_link",
            "secondary","secondary_link",
            "tertiary","tertiary_link",
            "unclassified",
            "residential",
            "living_street",
            "service",
        }
        self.way_ids = set()
        self.node_ids = set()

    def way(self, w):
        tags = w.tags
        highway = tags.get("highway")
        if highway not in self.allowed:
            return
        if "maxspeed" not in tags:
            return
        self.way_ids.add(w.id)
        for n in w.nodes:
            self.node_ids.add(n.ref)


class FilterWriter(osmium.SimpleHandler):
    def __init__(self, writer, way_ids, node_ids):
        super().__init__()
        self.writer = writer
        self.way_ids = way_ids
        self.node_ids = node_ids

    def node(self, n):
        if n.id in self.node_ids:
            self.writer.add_node(n)

    def way(self, w):
        if w.id in self.way_ids:
            self.writer.add_way(w)


def create_filtered_pbf(input_pbf: Path, output_pbf: Path):
    print("Collecting ways and nodes (pass 1)...")
    collector = WayCollector()
    collector.apply_file(str(input_pbf), locations=False)
    print(f"Ways kept: {len(collector.way_ids):,}")
    print(f"Nodes needed: {len(collector.node_ids):,}")

    print("Writing filtered PBF (pass 2)...")
    writer = osmium.SimpleWriter(str(output_pbf))
    handler = FilterWriter(writer, collector.way_ids, collector.node_ids)
    handler.apply_file(str(input_pbf), locations=False)
    writer.close()
    print(f"Filtered PBF saved to {output_pbf}")


# ---------- STATS ----------
class WayStats(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.allowed = {
            "motorway","motorway_link",
            "trunk","trunk_link",
            "primary","primary_link",
            "secondary","secondary_link",
            "tertiary","tertiary_link",
            "unclassified",
            "residential",
            "living_street",
            "service",
        }
        self.total_highway = 0
        self.total_maxspeed = 0

    def way(self, w):
        tags = w.tags
        highway = tags.get("highway")
        if highway not in self.allowed:
            return
        self.total_highway += 1
        if "maxspeed" in tags:
            self.total_maxspeed += 1


# ---------- CREATE DB ----------
def create_db(osm_file: Path, db_file: Path, optimize=False):
    conn = sqlite3.connect(db_file)
    cur = conn.cursor()
    cur.execute("PRAGMA journal_mode=OFF")
    cur.execute("PRAGMA synchronous=OFF")
    cur.execute("PRAGMA locking_mode=EXCLUSIVE")
    cur.execute("PRAGMA temp_store=MEMORY")
    cur.execute("PRAGMA cache_size=-200000")

    cur.execute("""
        CREATE TABLE IF NOT EXISTS speed_index(
            id INTEGER PRIMARY KEY,
            minLat REAL,
            maxLat REAL,
            minLon REAL,
            maxLon REAL
        )
    """)

    cur.execute("""
        CREATE TABLE IF NOT EXISTS speed_data(
            id INTEGER PRIMARY KEY,
            speed INTEGER
        ) WITHOUT ROWID
    """)

    conn.commit()
    handler = SpeedHandler(cur)
    conn.execute("BEGIN")
    handler.apply_file(str(osm_file), locations=True, idx="sparse_file_array")
    handler.flush()
    conn.commit()
    cur.execute("CREATE INDEX IF NOT EXISTS idx_lat ON speed_index(minLat,maxLat)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_lon ON speed_index(minLon,maxLon)")

    if optimize:
        print("Running VACUUM/ANALYZE...")
        cur.execute("VACUUM")
        cur.execute("ANALYZE")

    conn.close()


# ---------- PROCESS ----------
def run_process(continent, country, optimize=False):
    if continent not in CONTINENT_IDS:
        raise ValueError(f"Invalid continent: {continent}")

    countries, iso_map = fetch_countries_for_continent(continent)
    ISO_CODES.update(iso_map)

    if country not in countries:
        raise ValueError(f"Country {country} not found in continent {continent}")

    iso = ISO_CODES[country]
    db_file = Path(f"{iso}.sqlite")

    if db_file.exists():
        print(f"Deleting existing DB {db_file}")
        db_file.unlink()

    osm_file = download_osm(country, continent)

    print(f"Computing statistics for {country}...")
    stats = WayStats()
    stats.apply_file(str(osm_file), locations=False)
    print(f"Total highway ways: {stats.total_highway:,}")
    print(f"Total maxspeed ways: {stats.total_maxspeed:,}")
    print(f"Percentage with maxspeed tags: {stats.total_maxspeed / stats.total_highway * 100:.2f}%")

    # ---------- append statistics to file ----------
    today = datetime.datetime.now().strftime("%Y%m%d")
    stats_file = Path(f"statistics_{today}.txt")

    with stats_file.open("a", encoding="utf-8") as f:
        f.write(f"{country}\n")
        f.write(f"Total highway ways: {stats.total_highway:,}\n")
        f.write(f"Total maxspeed ways: {stats.total_maxspeed:,}\n")
        f.write(f"Percentage with maxspeed tags: {stats.total_maxspeed / stats.total_highway * 100:.2f}%\n\n")


    filtered_file = osm_file.with_name(osm_file.stem + "_filtered.osm.pbf")
    create_filtered_pbf(osm_file, filtered_file)

    print(f"Creating SQLite DB {db_file} ...")
    create_db(filtered_file, db_file, optimize)

    print(f"Compressing {db_file} with 7z...")
    proc = subprocess.run([
        "7z",
        "a",          # add to archive
        "-mx=9",      # max compression
        f"{db_file}.7z",
        str(db_file)
    ], check=True)

    # Delete original db_file after successful compression
    db_file.unlink()


    print("Cleaning up temporary files...")
    if osm_file.exists():
        osm_file.unlink()
    if filtered_file.exists():
        filtered_file.unlink()
    print("Done! SQLite DB ready.")


# ---------- MAIN CLI ----------
def main():
    parser = argparse.ArgumentParser(description="Create an automotive maxspeed SQLite database from Geofabrik extracts.")
    parser.add_argument("--continent", required=True, choices=CONTINENT_IDS, help="Continent name")
    parser.add_argument("country", help="Country ID from Geofabrik")
    parser.add_argument("-o", "--optimize", action="store_true", help="Optimize DB with VACUUM/ANALYZE")

    args = parser.parse_args()
    run_process(args.continent, args.country, optimize=args.optimize)


if __name__ == "__main__":
    main()