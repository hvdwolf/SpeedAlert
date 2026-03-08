#!/usr/bin/env python3

import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import threading
import osmium
import sqlite3
import re
from pathlib import Path
import requests

# ---------- CONFIG ----------
BATCH_SIZE = 5000

# Bounding box marge (~30–35 meter)
POINT_EPSILON = 0.0003

# Hard-coded Geofabrik continent IDs
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

# Cache: continent -> [country ids]
CONTINENTS = {c: [] for c in CONTINENT_IDS}

# Country id -> ISO code
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


# ---------- OSM HANDLER ----------
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

        nodes = w.nodes
        first = True

        for n in nodes:

            lat = n.lat
            lon = n.lon

            if first:
                minLat = maxLat = lat
                minLon = maxLon = lon
                first = False
            else:
                if lat < minLat: minLat = lat
                if lat > maxLat: maxLat = lat
                if lon < minLon: minLon = lon
                if lon > maxLon: maxLon = lon

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
def download_osm(country: str, continent: str, log, progress):
    url = f"https://download.geofabrik.de/{continent}/{country}-latest.osm.pbf"
    local_file = Path(f"{country}.osm.pbf")
    log(f"Downloading {url} ...")

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
                progress.set(int((downloaded / total) * 20))

    log(f"Saved to {local_file}")
    return local_file.resolve()


# ---------- PASS 1: collect ways and nodes ----------
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


# ---------- PASS 2: write filtered PBF ----------
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


# ---------- create filtered pbf ----------
def create_filtered_pbf(input_pbf: Path, output_pbf: Path, log):

    log("Pass 1: collecting ways and nodes...")

    collector = WayCollector()
    collector.apply_file(str(input_pbf), locations=False)

    log(f"Ways kept: {len(collector.way_ids):,}")
    log(f"Nodes needed: {len(collector.node_ids):,}")

    log("Pass 2: writing filtered PBF...")

    writer = osmium.SimpleWriter(str(output_pbf))

    handler = FilterWriter(
        writer,
        collector.way_ids,
        collector.node_ids
    )

    handler.apply_file(str(input_pbf), locations=False)

    writer.close()

    log("Filtered PBF created.")


# ---------- Ways/speed limit statistics ----------
class WayStats(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()

        # exact same allowed highway types als SpeedHandler
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

        # only count roads being used for cars
        if highway not in self.allowed:
            return

        self.total_highway += 1

        if "maxspeed" in tags:
            self.total_maxspeed += 1


# ---------- CREATE DB ----------
def create_db(osm_file: Path, db_file: Path, optimize, log, progress):

    conn = sqlite3.connect(db_file)
    cur = conn.cursor()

    cur.execute("PRAGMA journal_mode=OFF")
    cur.execute("PRAGMA synchronous=OFF")
    cur.execute("PRAGMA locking_mode=EXCLUSIVE")
    cur.execute("PRAGMA temp_store=MEMORY")
    cur.execute("PRAGMA cache_size=-200000")

    cur.execute("""
        CREATE TABLE speed_index(
            id INTEGER PRIMARY KEY,
            minLat REAL,
            maxLat REAL,
            minLon REAL,
            maxLon REAL
        )
    """)

    cur.execute("""
        CREATE TABLE speed_data(
            id INTEGER PRIMARY KEY,
            speed INTEGER
        ) WITHOUT ROWID
    """)

    conn.commit()

    handler = SpeedHandler(cur)

    conn.execute("BEGIN")

    handler.apply_file(
        str(osm_file),
        locations=True,
        idx="sparse_file_array"
    )

    handler.flush()

    conn.commit()

    cur.execute("CREATE INDEX idx_lat ON speed_index(minLat,maxLat)")
    cur.execute("CREATE INDEX idx_lon ON speed_index(minLon,maxLon)")

    progress.set(90)

    if optimize:
        log("Running VACUUM...")
        cur.execute("VACUUM")
        cur.execute("ANALYZE")

    conn.close()

    progress.set(100)


# ---------- PROCESS ----------
def run_process(continent, country, optimize, log, progress, output_dir):
    try:
        iso = ISO_CODES.get(country)
        if not iso:
            raise ValueError(f"No ISO code found for '{country}'")

        db_file = Path(output_dir) / f"{iso}.sqlite"

        if db_file.exists():
            log(f"Deleting existing DB {db_file} ...")
            db_file.unlink()

        osm_file = download_osm(country, continent, log, progress)

        log(f"Doing statistics on {country} ...")
        stats = WayStats()
        stats.apply_file(str(osm_file), locations=False)

        log(f"Total highway ways: {stats.total_highway:,}")
        log(f"Total maxspeed ways: {stats.total_maxspeed:,}")
        log(f"Percentage with maxspeed tags: {stats.total_maxspeed / stats.total_highway * 100:.2f}%")

        progress.set(30)

        filtered_file = osm_file.with_name(osm_file.stem + "_filtered.osm.pbf")
        create_filtered_pbf(osm_file, filtered_file, log)

        # create_db(osm_file, db_file, optimize, log, progress)
        create_db(filtered_file, db_file, optimize, log, progress)

        log("Deleting intermediate PBF files ...")
        if osm_file.exists():
            osm_file.unlink()
        if filtered_file.exists():
            filtered_file.unlink()

        log("Cleanup done. Only the SQLite DB remains.")

    except Exception as e:
        log(f"ERROR: {e}")
        messagebox.showerror("Error", str(e))


# ---------- GUI ----------
def main_gui():
    root = tk.Tk()
    root.title("OSM Maxspeed DB Builder (Automotive)")

    frame = ttk.Frame(root, padding=15)
    frame.grid(row=0, column=0, sticky="nsew")

    for col in range(4):
        frame.grid_columnconfigure(col, weight=1)

    intro = (
        "Create an automotive maxspeed SQLite database from Geofabrik extracts.\n"
        "The database will be saved using the ISO code, e.g. nl.sqlite, de.sqlite, fr.sqlite."
    )

    ttk.Label(frame, text=intro, wraplength=700, justify="left").grid(
        row=0, column=0, columnspan=4, pady=(0, 10), sticky="we"
    )

    ttk.Separator(frame).grid(row=1, column=0, columnspan=4, sticky="we", pady=(0, 15))

    ttk.Label(frame, text="Continent:").grid(row=2, column=0, sticky="w")
    continent_var = tk.StringVar()
    continent_cb = ttk.Combobox(frame, textvariable=continent_var, values=sorted(CONTINENT_IDS), state="readonly")
    continent_cb.grid(row=2, column=1, sticky="we")

    ttk.Label(frame, text="Country:").grid(row=3, column=0, sticky="w")
    country_var = tk.StringVar()
    country_cb = ttk.Combobox(frame, textvariable=country_var, state="readonly")
    country_cb.grid(row=3, column=1, sticky="we")

    ttk.Label(frame, text="Output folder:").grid(row=4, column=0, sticky="w")
    output_var = tk.StringVar(value=str(Path.cwd()))
    ttk.Entry(frame, textvariable=output_var).grid(row=4, column=1, sticky="we")

    def choose_output_folder():
        folder = filedialog.askdirectory()
        if folder:
            output_var.set(folder)

    ttk.Button(frame, text="Choose...", command=choose_output_folder).grid(row=4, column=2)

    def reload_lists():
        global CONTINENTS, ISO_CODES
        CONTINENTS = {c: [] for c in CONTINENT_IDS}
        ISO_CODES = {}
        country_cb["values"] = []
        continent_var.set("")
        country_var.set("")
        messagebox.showinfo("Reloaded", "Country cache cleared.")

    ttk.Button(frame, text="Reload country list", command=reload_lists).grid(row=2, column=2)

    optimize_var = tk.BooleanVar(value=False)
    ttk.Checkbutton(frame, text="Optimize DB (VACUUM/ANALYZE)", variable=optimize_var).grid(row=5, column=1, sticky="w")

    progress_var = tk.IntVar()
    ttk.Progressbar(frame, variable=progress_var, maximum=100).grid(row=6, column=0, columnspan=4, sticky="we", pady=10)

    log_box = tk.Text(frame, width=90, height=20)
    log_box.grid(row=7, column=0, columnspan=4, pady=10)

    def log(msg):
        log_box.insert("end", msg + "\n")
        log_box.see("end")

    def update_countries(event):
        cont = continent_var.get()
        if not cont:
            return
        if not CONTINENTS[cont]:
            try:
                countries, iso_map = fetch_countries_for_continent(cont)
                CONTINENTS[cont] = countries
                ISO_CODES.update(iso_map)
            except Exception as e:
                messagebox.showerror("Error", f"Failed to fetch countries:\n{e}")
                return
        country_cb["values"] = CONTINENTS[cont]
        country_var.set("")

    continent_cb.bind("<<ComboboxSelected>>", update_countries)

    def on_run():
        cont = continent_var.get()
        country = country_var.get()
        output_dir = output_var.get()

        if not cont or not country:
            messagebox.showwarning("Missing input", "Please select both continent and country.")
            return

        progress_var.set(0)

        threading.Thread(
            target=run_process,
            args=(cont, country, optimize_var.get(), log, progress_var, output_dir),
            daemon=True,
        ).start()

    ttk.Button(frame, text="Run", command=on_run).grid(row=8, column=0, columnspan=4, pady=10)

    root.mainloop()


if __name__ == "__main__":
    main_gui()
