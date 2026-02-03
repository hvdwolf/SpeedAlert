package xyz.hvdw.speedalert

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.*

class MultiProviderSpeedLimitRepository {

    private val client = OkHttpClient()

    // Cache
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastLimit: Int? = null
    private var lastFetchTime = 0L

    suspend fun getSpeedLimit(lat: Double, lon: Double): Int? = withContext(Dispatchers.IO) {

        val now = System.currentTimeMillis()

        // Cache: 10 seconds + within 50 meters
        if (lastLimit != null &&
            now - lastFetchTime < 10_000 &&
            distanceMeters(lat, lon, lastLat, lastLon) < 50
        ) {
            return@withContext lastLimit
        }

        // Try providers in order
        val providers = listOf(
            ::queryOverpass,
            ::queryNominatim,
            ::queryOsmApi,
            ::fallbackCountryDefaults
        )

        for (provider in providers) {
            val limit = provider(lat, lon)
            if (limit != null) {
                lastLat = lat
                lastLon = lon
                lastLimit = limit
                lastFetchTime = now
                return@withContext limit
            }
        }

        return@withContext null
    }

    // ---------------------------------------------------------
    // PROVIDER 1 — Overpass API
    // ---------------------------------------------------------
    private fun queryOverpass(lat: Double, lon: Double): Int? {
        val query = """
            [out:json];
            (
              way(around:50,$lat,$lon)[maxspeed];
              node(around:50,$lat,$lon)[maxspeed];
            );
            out tags;
        """.trimIndent()

        val url = "https://overpass-api.de/api/interpreter?data=${Uri.encode(query)}"

        return try {
            val res = client.newCall(Request.Builder().url(url).build()).execute()
            val body = res.body?.string() ?: return null

            val json = JSONObject(body)
            val elements = json.optJSONArray("elements") ?: return null
            if (elements.length() == 0) return null

            val tags = elements.getJSONObject(0).optJSONObject("tags") ?: return null
            val maxspeed = tags.optString("maxspeed", null) ?: return null

            extractSpeed(maxspeed)
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------
    // PROVIDER 2 — Nominatim Reverse Geocoding
    // ---------------------------------------------------------
    private fun queryNominatim(lat: Double, lon: Double): Int? {
        val url =
            "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&extratags=1"

        return try {
            val res = client.newCall(Request.Builder().url(url).build()).execute()
            val body = res.body?.string() ?: return null

            val json = JSONObject(body)
            val tags = json.optJSONObject("extratags") ?: return null
            val maxspeed = tags.optString("maxspeed", null) ?: return null

            extractSpeed(maxspeed)
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------
    // PROVIDER 3 — OSM API nearest way/node
    // ---------------------------------------------------------
    private fun queryOsmApi(lat: Double, lon: Double): Int? {
        val url = "https://api.openstreetmap.org/api/0.6/map?bbox=${lon-0.0003},${lat-0.0003},${lon+0.0003},${lat+0.0003}"

        return try {
            val res = client.newCall(Request.Builder().url(url).build()).execute()
            val body = res.body?.string() ?: return null

            val json = JSONObject(body)
            val elements = json.optJSONArray("elements") ?: return null

            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                if (el.optString("type") == "way") {
                    val tags = el.optJSONObject("tags") ?: continue
                    val maxspeed = tags.optString("maxspeed", null) ?: continue
                    return extractSpeed(maxspeed)
                }
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------
    // PROVIDER 4 — Country fallback defaults
    // ---------------------------------------------------------
    private fun fallbackCountryDefaults(lat: Double, lon: Double): Int? {
        val country = reverseCountryCode(lat, lon) ?: return null

        return when (country) {

            // Netherlands
            "NL" -> 50

            // Germany
            "DE" -> 50

            // Belgium
            "BE" -> 50

            // France
            "FR" -> 50

            // UK
            "GB" -> 30

            // USA (default urban)
            "US" -> 25

            // Canada
            "CA" -> 50

            // Australia
            "AU" -> 50

            // Default fallback
            else -> 50
        }
    }

    // Reverse geocode country code (free)
    private fun reverseCountryCode(lat: Double, lon: Double): String? {
        val url =
            "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=3&addressdetails=1"

        return try {
            val res = client.newCall(Request.Builder().url(url).build()).execute()
            val body = res.body?.string() ?: return null

            val json = JSONObject(body)
            val addr = json.optJSONObject("address") ?: return null
            addr.optString("country_code", null)?.uppercase()
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------
    private fun extractSpeed(raw: String): Int? {
        return Regex("""\d+""").find(raw)?.value?.toIntOrNull()
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
