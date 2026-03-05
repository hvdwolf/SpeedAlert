package xyz.hvdw.speedalert

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

class LocalSpeedDbManager(private val service: DrivingService) {

    companion object {
        private const val TAG = "LocalSpeedDbManager"
        private const val DB_DIR = "/sdcard/SpeedAlert"
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
            SELECT speed_data.speed
            FROM speed_index
            JOIN speed_data ON speed_index.id = speed_data.id
            WHERE minLat <= ? AND maxLat >= ?
              AND minLon <= ? AND maxLon >= ?
            LIMIT 1
        """

        return try {
            db.rawQuery(sql, arrayOf(
                lat.toString(), lat.toString(),
                lon.toString(), lon.toString()
            )).use { c ->
                if (c.moveToFirst()) {
                    val speed = c.getInt(0)
                    service.logExternal("Local DB: HIT → speed=$speed at ($lat,$lon)")
                    speed
                } else {
                    service.logExternal("Local DB: MISS at ($lat,$lon)")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DB lookup failed: ${e.message}")
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
