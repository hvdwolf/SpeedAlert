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
    match = re.search(r"\d+", maxspeed_str)
    if match:
        return int(match.group(0))
    return None


# ---------- CAMERA HANDLER ----------
class CameraHandler(osmium.SimpleHandler):
    def __init__(self, cursor):
        super().__init__()
        self.cur = cursor
        self.rows = []

    def flush(self):
        if not self.rows:
            return
        self.cur.executemany(
            "INSERT OR REPLACE INTO cameras (id, lat, lon, type) VALUES (?,?,?,?)",
            self.rows
        )
        self.rows.clear()

    def node(self, n):
        tags = n.tags

        cam_type = None

        if tags.get("highway") == "speed_camera" and tags.get("camera:traffic_signals") == "yes":
            cam_type = "combined"
        elif tags.get("highway") == "speed_camera":
            cam_type = "speed"
        elif tags.get("traffic_signals:camera") == "yes":
            cam_type = "redlight"

        if cam_type is None:
            return

        if not n.location.valid():
            return

        lat = n.location.lat
        lon = n.location.lon

        self.rows.append((int(n.id), lat, lon, cam_type))

        if len(self.rows) >= BATCH_SIZE:
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


# ---------- SPEED FILTER (WAYS + NODES) ----------
class WayCollector(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.allowed = {
            "motorway", "motorway_link",
            "trunk", "trunk_link",
            "primary", "primary_link",
            "secondary", "secondary_link",
            "tertiary", "tertiary_link",
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


def create_speed_filtered_pbf(input_pbf: Path, output_pbf: Path):
    if output_pbf.exists():
        print(f"Removing existing speed-filtered PBF {output_pbf}")
        output_pbf.unlink()

    print("Collecting ways and nodes for speed (pass 1)...")
    collector = WayCollector()
    collector.apply_file(str(input_pbf), locations=False)
    print(f"Ways kept: {len(collector.way_ids):,}")
    print(f"Nodes needed: {len(collector.node_ids):,}")

    print("Writing speed-filtered PBF (pass 2)...")
    writer = osmium.SimpleWriter(str(output_pbf))
    handler = FilterWriter(writer, collector.way_ids, collector.node_ids)
    handler.apply_file(str(input_pbf), locations=False)
    writer.close()
    print(f"Speed-filtered PBF saved to {output_pbf}")


# ---------- CAMERA FILTER (NODES) ----------
class CameraCollector(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.node_ids = set()

    def node(self, n):
        tags = n.tags
        if tags.get("highway") == "speed_camera" or tags.get("traffic_signals:camera") == "yes":
            self.node_ids.add(n.id)


class CameraFilterWriter(osmium.SimpleHandler):
    def __init__(self, writer, node_ids):
        super().__init__()
        self.writer = writer
        self.node_ids = node_ids

    def node(self, n):
        if n.id in self.node_ids:
            self.writer.add_node(n)


def create_camera_filtered_pbf(input_pbf: Path, output_pbf: Path):
    if output_pbf.exists():
        print(f"Removing existing camera-filtered PBF {output_pbf}")
        output_pbf.unlink()

    print("Collecting camera nodes (pass 1)...")
    collector = CameraCollector()
    collector.apply_file(str(input_pbf), locations=False)
    print(f"Camera nodes found: {len(collector.node_ids):,}")

    print("Writing camera-filtered PBF (pass 2)...")
    writer = osmium.SimpleWriter(str(output_pbf))
    handler = CameraFilterWriter(writer, collector.node_ids)
    handler.apply_file(str(input_pbf), locations=True)
    writer.close()
    print(f"Camera-filtered PBF saved to {output_pbf}")


# ---------- CREATE CAMERA DB ----------
def create_camera_db(osm_file: Path, db_file: Path, optimize=False):
    conn = sqlite3.connect(db_file)
    cur = conn.cursor()
    cur.execute("PRAGMA journal_mode=OFF")
    cur.execute("PRAGMA synchronous=OFF")
    cur.execute("PRAGMA locking_mode=EXCLUSIVE")
    cur.execute("PRAGMA temp_store=MEMORY")
    cur.execute("PRAGMA cache_size=-200000")

    cur.execute("""
        CREATE TABLE IF NOT EXISTS cameras(
            id INTEGER PRIMARY KEY,
            lat REAL NOT NULL,
            lon REAL NOT NULL,
            type TEXT NOT NULL
        )
    """)

    conn.commit()
    handler = CameraHandler(cur)
    conn.execute("BEGIN")
    handler.apply_file(str(osm_file), locations=True, idx="sparse_file_array")
    handler.flush()
    conn.commit()

    cur.execute("CREATE INDEX IF NOT EXISTS idx_cam_latlon ON cameras(lat, lon)")

    if optimize:
        print("Running VACUUM/ANALYZE on camera DB...")
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
    speed_db_file = Path(f"{iso}.sqlite")
    camera_db_file = Path(f"{iso}-camera.sqlite")

    for dbf in (speed_db_file, camera_db_file):
        if dbf.exists():
            print(f"Deleting existing DB {dbf}")
            dbf.unlink()

    osm_file = download_osm(country, continent)

    camera_filtered = osm_file.with_name(osm_file.stem + "_camera_filtered.osm.pbf")

    # Remove old filtered file if it exists
    if camera_filtered.exists():
        print(f"Removing existing camera-filtered PBF {camera_filtered}")
        camera_filtered.unlink()

    print("Filtering cameras with osmium (very fast)...")
    subprocess.run([
        "osmium", "tags-filter",
        str(osm_file),
        "n/highway=speed_camera",
        "n/traffic_signals:camera=yes",
        "-o", str(camera_filtered)
    ], check=True)


    #print(f"Creating speed SQLite DB {speed_db_file} ...")
    #create_speed_db(speed_filtered, speed_db_file, optimize)

    print(f"Creating camera SQLite DB {camera_db_file} ...")
    create_camera_db(camera_filtered, camera_db_file, optimize)


    print(f"Compressing {camera_db_file} with 7z...")
    subprocess.run([
        "7z",
        "a",
        "-mx=9",
        f"{camera_db_file}.7z",
        str(camera_db_file)
    ], check=True)

    #speed_db_file.unlink()
    camera_db_file.unlink()

    print("Cleaning up temporary files...")
    #for f in (osm_file, speed_filtered, camera_filtered):
    for f in (osm_file, camera_filtered):
        if f.exists():
            f.unlink()
    print("Done! SQLite DBs ready.")


# ---------- MAIN CLI ----------
def main():
    parser = argparse.ArgumentParser(
        description="Create automotive maxspeed and camera SQLite databases from Geofabrik extracts."
    )
    parser.add_argument("--continent", required=True, choices=CONTINENT_IDS, help="Continent name")
    parser.add_argument("country", help="Country ID from Geofabrik")
    parser.add_argument("-o", "--optimize", action="store_true", help="Optimize DBs with VACUUM/ANALYZE")

    args = parser.parse_args()
    run_process(args.continent, args.country, optimize=args.optimize)


if __name__ == "__main__":
    main()
