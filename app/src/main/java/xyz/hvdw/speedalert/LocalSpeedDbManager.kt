package xyz.hvdw.speedalert

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

class LocalSpeedDbManager(private val service: DrivingService) {

    companion object {
        private const val TAG = "LocalSpeedDbManager"
        private const val DB_DIR = "/storage/emulated/0/Android/media/xyz.hvdw.speedalert"
    }

    private var dbMap: Map<String, File> = emptyMap()
    private var activeDb: SQLiteDatabase? = null
    private var activeCountry: String? = null

    private var activeSpeedDbFile: File? = null
    private var activeCameraDbFile: File? = null

    private var cameraDb: SQLiteDatabase? = null
    private val cameraManager = CameraManager()

    // ---------------------------------------------------------
    // INITIALIZATION
    // ---------------------------------------------------------
    fun initialize() {
        dbMap = findLocalSpeedDatabases()
        Log.i(TAG, "Found local speed DBs: $dbMap")
        service.logExternal("Local DB: found speed DBs $dbMap")

        // Scan for camera DBs
        val dir = File(DB_DIR)
        val camFiles = dir.listFiles { f ->
            f.isFile && f.name.lowercase().endsWith("-camera.sqlite")
        } ?: emptyArray()

        for (file in camFiles) {
            try {
                cameraDb = SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
                cameraManager.setDatabase(cameraDb)
                activeCameraDbFile = file
                service.logExternal("Local DB: loaded camera DB ${file.name}")
            } catch (e: Exception) {
                service.logExternal("Local DB: failed to open camera DB ${file.name}: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------
    // SCAN FOR SPEED *.sqlite FILES (CREATE FOLDER IF MISSING)
    // ---------------------------------------------------------
    private fun findLocalSpeedDatabases(): Map<String, File> {
        val dir = File(DB_DIR)

        if (!dir.exists()) {
            service.logExternal("Local DB: folder not found at $DB_DIR — creating it")

            val ok = dir.mkdirs()
            if (!ok) {
                Log.e(TAG, "Failed to create folder: $DB_DIR")
                service.logExternal("Local DB: ERROR — failed to create folder $DB_DIR")
                return emptyMap()
            }

            Log.i(TAG, "Created folder: $DB_DIR")
            service.logExternal("Local DB: created folder $DB_DIR")
        }

        val files = dir.listFiles { f ->
            f.isFile && f.extension.lowercase() == "sqlite"
        } ?: return emptyMap()

        val map = mutableMapOf<String, File>()

        for (file in files) {
            val name = file.nameWithoutExtension.lowercase()

            // speed DB: nl.sqlite → key "nl"
            if (!name.endsWith("-camera")) {
                map[name] = file
            }
        }

        return map
    }

    // ---------------------------------------------------------
    // SELECT SPEED DB BASED ON COUNTRY CODE
    // ---------------------------------------------------------
    fun updateCountry(countryCode: String?) {
        if (countryCode == null) {
            service.logExternal("Local DB: updateCountry called with null")
            return
        }

        val cc = countryCode.lowercase()
        service.logExternal("Local DB: selecting speed DB for country $cc")

        // Already using correct DB
        if (cc == activeCountry) {
            service.logExternal("Local DB: already using DB for $cc")
            return
        }

        // Close previous DB
        activeDb?.close()
        activeDb = null
        activeCountry = null

        val file = dbMap[cc]
        if (file == null) {
            Log.i(TAG, "No local speed DB for country: $cc")
            service.logExternal("Local DB: no speed DB for country $cc")
            return
        }

        activeDb = openSpeedDb(file)
        if (activeDb != null) {
            activeCountry = cc
            activeSpeedDbFile = file
            Log.i(TAG, "Using local speed DB for country: $cc")
            service.logExternal("Local DB: using ${file.name}")
        } else {
            service.logExternal("Local DB: failed to open ${file.name}")
        }
    }

    // ---------------------------------------------------------
    // OPEN SPEED SQLITE DB (READ-ONLY)
    // ---------------------------------------------------------
    private fun openSpeedDb(file: File): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open DB ${file.name}: ${e.message}")
            service.logExternal("Local DB: error opening ${file.name}: ${e.message}")
            null
        }
    }

    // ---------------------------------------------------------
    // LOOKUP SPEED FROM LOCAL DB
    // ---------------------------------------------------------
    fun lookupSpeed(lat: Double, lon: Double): Int? {
        val db = activeDb ?: return null

        val sql = """
            SELECT 
                speed_index.id,
                speed_index.minLat,
                speed_index.maxLat,
                speed_index.minLon,
                speed_index.maxLon,
                speed_index.polyline,
                speed_data.speed
            FROM speed_index
            JOIN speed_data ON speed_index.id = speed_data.id
            WHERE minLat <= ? AND maxLat >= ?
              AND minLon <= ? AND maxLon >= ?
        """

        return try {
            db.rawQuery(
                sql,
                arrayOf(
                    lat.toString(), lat.toString(),
                    lon.toString(), lon.toString()
                )
            ).use { c ->

                if (!c.moveToFirst()) {
                    service.logExternal("Local DB: MISS at ($lat,$lon)")
                    return null
                }

                var bestSpeed = -1
                var bestDist = Double.MAX_VALUE

                do {
                    val minLat = c.getDouble(1)
                    val maxLat = c.getDouble(2)
                    val minLon = c.getDouble(3)
                    val maxLon = c.getDouble(4)
                    val polyline = c.getString(5)
                    val speed = c.getInt(6)

                    // Quick reject: if bounding box is huge or weird, you could skip here if needed

                    if (!polyline.isNullOrEmpty()) {
                        val points = decodePolyline(polyline)
                        if (points.size >= 2) {
                            var localBest = Double.MAX_VALUE
                            for (i in 0 until points.size - 1) {
                                val (aLat, aLon) = points[i]
                                val (bLat, bLon) = points[i + 1]
                                val d = pointToSegmentDistSq(lat, lon, aLat, aLon, bLat, bLon)
                                if (d < localBest) {
                                    localBest = d
                                }
                            }

                            if (localBest < bestDist) {
                                bestDist = localBest
                                bestSpeed = speed
                            }
                        } else if (points.size == 1) {
                            val (pLat, pLon) = points[0]
                            val dLat = lat - pLat
                            val dLon = lon - pLon
                            val d = dLat * dLat + dLon * dLon
                            if (d < bestDist) {
                                bestDist = d
                                bestSpeed = speed
                            }
                        }
                    }

                } while (c.moveToNext())

                if (bestSpeed > 0) {
                    service.logExternal("Local DB: HIT → speed=$bestSpeed at ($lat,$lon)")
                    bestSpeed
                } else {
                    service.logExternal("Local DB: MISS at ($lat,$lon)")
                    null
                }
            }
        } catch (e: Exception) {
            service.logExternal("Local DB: lookup error → ${e.message}")
            null
        }
    }


    // ---------------------------------------------------------
    // CAMERA MANAGER ACCESS
    // ---------------------------------------------------------
    fun getCameraManager(): CameraManager? {
        return if (cameraDb != null) cameraManager else null
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------
    fun close() {
        activeDb?.close()
        activeDb = null
        activeCountry = null

        cameraManager.close()
        cameraDb?.close()
        cameraDb = null

        service.logExternal("Local DB: closed")
    }

    fun getActiveSpeedDbName(): String? {
        return activeSpeedDbFile?.name
    }

    fun getCameraDbName(): String? {
        return activeCameraDbFile?.name
    }

    // ---------------------------------------------------------
    // AS OF 2.2: ADD GOOGLE POLYLINE FOR IMPROVED ACCURACY
    // ---------------------------------------------------------
    // Decode Google encoded polyline into a list of (lat, lon)
    private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val len = encoded.length
        var index = 0
        var lat = 0
        var lon = 0
        val path = ArrayList<Pair<Double, Double>>()

        while (index < len) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if ((result and 1) != 0) (result.inv() shr 1) else (result shr 1)
            lat += dLat

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLon = if ((result and 1) != 0) (result.inv() shr 1) else (result shr 1)
            lon += dLon

            path.add(Pair(lat / 1e5, lon / 1e5))
        }

        return path
    }

    // Squared distance from point P to segment AB in lat/lon space
    private fun pointToSegmentDistSq(
        lat: Double,
        lon: Double,
        aLat: Double,
        aLon: Double,
        bLat: Double,
        bLon: Double
    ): Double {
        val vx = bLat - aLat
        val vy = bLon - aLon
        val wx = lat - aLat
        val wy = lon - aLon

        val c1 = vx * wx + vy * wy
        if (c1 <= 0.0) {
            val dLat = lat - aLat
            val dLon = lon - aLon
            return dLat * dLat + dLon * dLon
        }

        val c2 = vx * vx + vy * vy
        if (c2 <= c1) {
            val dLat = lat - bLat
            val dLon = lon - bLon
            return dLat * dLat + dLon * dLon
        }

        val t = c1 / c2
        val projLat = aLat + t * vx
        val projLon = aLon + t * vy
        val dLat = lat - projLat
        val dLon = lon - projLon
        return dLat * dLat + dLon * dLon
    }

}
