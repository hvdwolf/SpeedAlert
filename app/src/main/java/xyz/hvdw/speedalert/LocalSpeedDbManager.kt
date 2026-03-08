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

    // ---------------------------------------------------------
    // INITIALIZATION
    // ---------------------------------------------------------
    fun initialize() {
        dbMap = findLocalSpeedDatabases()
        Log.i(TAG, "Found local DBs: $dbMap")
        service.logExternal("Local DB: found databases $dbMap")
    }

    // ---------------------------------------------------------
    // SCAN FOR *.sqlite FILES (CREATE FOLDER IF MISSING)
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

        return files.associateBy { file ->
            file.nameWithoutExtension.lowercase()  // "nl", "de", "fr"
        }
    }

    // ---------------------------------------------------------
    // SELECT DB BASED ON COUNTRY CODE
    // ---------------------------------------------------------
    fun updateCountry(countryCode: String?) {
        if (countryCode == null) {
            service.logExternal("Local DB: updateCountry called with null")
            return
        }

        val cc = countryCode.lowercase()
        service.logExternal("Local DB: selecting database for country $cc")

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
            Log.i(TAG, "No local DB for country: $cc")
            service.logExternal("Local DB: no database for country $cc")
            return
        }

        activeDb = openSpeedDb(file)
        if (activeDb != null) {
            activeCountry = cc
            Log.i(TAG, "Using local speed DB for country: $cc")
            service.logExternal("Local DB: using ${file.name}")
        } else {
            service.logExternal("Local DB: failed to open ${file.name}")
        }
    }

    // ---------------------------------------------------------
    // OPEN SQLITE DB (READ-ONLY)
    // ---------------------------------------------------------
    private fun openSpeedDb(file: File): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS //or SQLiteDatabase.OPEN_IMMUTABLE
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
                speed_data.speed
            FROM speed_index
            JOIN speed_data ON speed_index.id = speed_data.id
            WHERE minLat <= ? AND maxLat >= ?
              AND minLon <= ? AND maxLon >= ?
        """

        return try {
            db.rawQuery(sql, arrayOf(
                lat.toString(), lat.toString(),
                lon.toString(), lon.toString()
            )).use { c ->

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
                    val speed = c.getInt(5)

                    val centerLat = (minLat + maxLat) / 2
                    val centerLon = (minLon + maxLon) / 2

                    val dLat = lat - centerLat
                    val dLon = lon - centerLon
                    val dist = dLat * dLat + dLon * dLon  // squared distance

                    if (dist < bestDist) {
                        bestDist = dist
                        bestSpeed = speed
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
    // CLEANUP
    // ---------------------------------------------------------
    fun close() {
        activeDb?.close()
        activeDb = null
        activeCountry = null
        service.logExternal("Local DB: closed")
    }
}
