package xyz.hvdw.speedalert

import android.database.sqlite.SQLiteDatabase
import kotlin.math.*

data class CameraHit(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val type: String,
    val distanceMeters: Double
)

class CameraManager {

    private var db: SQLiteDatabase? = null

    fun setDatabase(database: SQLiteDatabase?) {
        db = database
    }

    fun close() {
        db?.close()
        db = null
    }

    fun findNearbyCameras(
        lat: Double,
        lon: Double,
        headingDeg: Float?,
        maxDistanceMeters: Double = 300.0
    ): List<CameraHit> {

        val database = db ?: return emptyList()

        val dLat = maxDistanceMeters / 111000.0
        val dLon = maxDistanceMeters / (111000.0 * cos(Math.toRadians(lat)))

        val minLat = lat - dLat
        val maxLat = lat + dLat
        val minLon = lon - dLon
        val maxLon = lon + dLon

        val hits = mutableListOf<CameraHit>()

        val cursor = database.rawQuery(
            "SELECT id, lat, lon, type FROM cameras " +
            "WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
            arrayOf(minLat.toString(), maxLat.toString(), minLon.toString(), maxLon.toString())
        )

        cursor.use {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val camLat = cursor.getDouble(1)
                val camLon = cursor.getDouble(2)
                val type = cursor.getString(3)

                val dist = haversineMeters(lat, lon, camLat, camLon)
                if (dist <= maxDistanceMeters) {

                    if (headingDeg != null) {
                        if (!isCameraInFront(lat, lon, camLat, camLon, headingDeg)) {
                            continue
                        }
                    }

                    hits.add(CameraHit(id, camLat, camLon, type, dist))
                }
            }
        }

        return hits.sortedBy { it.distanceMeters }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun isCameraInFront(
        lat: Double,
        lon: Double,
        camLat: Double,
        camLon: Double,
        headingDeg: Float,
        toleranceDeg: Float = 45f
    ): Boolean {

        val bearingToCam = bearingDegrees(lat, lon, camLat, camLon)

        var diff = abs(bearingToCam - headingDeg)
        if (diff > 180) diff = 360 - diff

        return diff <= toleranceDeg
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360) % 360).toFloat()
    }
}
