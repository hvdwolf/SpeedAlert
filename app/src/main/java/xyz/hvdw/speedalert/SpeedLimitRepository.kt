package xyz.hvdw.speedalert

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class SpeedLimitResult(
    val speedKmh: Int,
    val limitKmh: Int,
    val source: String
)

class SpeedLimitRepository(private val context: Context) {

    private val web = WebViewFetcher(context)

    // Multiple Overpass servers for reliability
    private val overpassServers = listOf(
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://overpass.openstreetmap.fr/api/interpreter",
        "https://overpass.nchc.org.tw/api/interpreter"
    )

    fun getSpeedLimit(lat: Double, lon: Double): SpeedLimitResult {
        log("Repo: start lookup for $lat,$lon")

        // 1) RAW OVERPASS (multi-server)
        val rawOverpass = tryRawOverpass(lat, lon)
        if (rawOverpass > 0) {
            return SpeedLimitResult(-1, rawOverpass, "raw-overpass")
        }

        // 2) RAW NOMINATIM
        val rawCountry = tryRawNominatim(lat, lon)
        if (rawCountry != null) {
            return SpeedLimitResult(-1, -1, "raw-nominatim:$rawCountry")
        }

        // 3) WEBVIEW OVERPASS (multi-server)
        val webOverpass = tryWebOverpass(lat, lon)
        if (webOverpass > 0) {
            return SpeedLimitResult(-1, webOverpass, "web-overpass")
        }

        // 4) WEBVIEW NOMINATIM
        val webCountry = tryWebNominatim(lat, lon)
        if (webCountry != null) {
            return SpeedLimitResult(-1, -1, "web-nominatim:$webCountry")
        }

        // 5) TOTAL FAILURE
        log("Repo: all methods failed → fallback")
        return SpeedLimitResult(-1, -1, "none")
    }

    // ---------------------------------------------------------
    // IMPROVED OVERPASS QUERY
    // ---------------------------------------------------------
    private fun buildOverpassQuery(lat: Double, lon: Double): String {
        val q = """
            [out:json][timeout:5];
            way(around:40,$lat,$lon)
              ["highway"]
              ["maxspeed"]
              [highway!~"^(cycleway|footway|path|track|service|bridleway|steps|living_street)$"];
            out tags geom;
        """.trimIndent()
        return URLEncoder.encode(q, "UTF-8")
    }

    // ---------------------------------------------------------
    // RAW OVERPASS (multi-server)
    // ---------------------------------------------------------
    private fun tryRawOverpass(lat: Double, lon: Double): Int {
        val query = buildOverpassQuery(lat, lon)

        for (server in overpassServers) {
            val url = "$server?data=$query"
            log("Trying Overpass RAW: $server")

            val body = fetchRaw(url)
            if (body != null) {
                val limit = parseOverpass(body, lat, lon)
                if (limit > 0) return limit
            }
        }

        return -1
    }

    // ---------------------------------------------------------
    // WEBVIEW OVERPASS (multi-server)
    // ---------------------------------------------------------
    private fun tryWebOverpass(lat: Double, lon: Double): Int {
        val query = buildOverpassQuery(lat, lon)

        for (server in overpassServers) {
            val url = "$server?data=$query"
            log("Trying Overpass WebView: $server")

            var result: Int? = null
            val latch = Object()

            web.fetch(url) { body ->
                result = if (body != null) parseOverpass(body, lat, lon) else -1
                synchronized(latch) { latch.notify() }
            }

            synchronized(latch) { latch.wait(9000) }

            if (result != null && result!! > 0) return result!!
        }

        return -1
    }

    // ---------------------------------------------------------
    // IMPROVED OVERPASS PARSER (distance-aware)
    // ---------------------------------------------------------
    private fun parseOverpass(body: String, lat: Double, lon: Double): Int {
        return try {
            val root = JSONObject(body)
            val elements = root.optJSONArray("elements") ?: return -1

            var bestSpeed = -1
            var bestDistance = Double.MAX_VALUE

            for (i in 0 until elements.length()) {
                val el = elements.optJSONObject(i) ?: continue
                val tags = el.optJSONObject("tags") ?: continue

                val maxspeed = tags.optString("maxspeed", "")
                val speed = maxspeed.filter { it.isDigit() }.toIntOrNull() ?: continue

                val geometry = el.optJSONArray("geometry") ?: continue

                // Compute minimum distance to any node in the way
                for (j in 0 until geometry.length()) {
                    val node = geometry.optJSONObject(j) ?: continue
                    val nLat = node.optDouble("lat")
                    val nLon = node.optDouble("lon")

                    val dist = haversine(lat, lon, nLat, nLon)
                    if (dist < bestDistance) {
                        bestDistance = dist
                        bestSpeed = speed
                    }
                }
            }

            bestSpeed
        } catch (e: Exception) {
            log("Overpass parse error: ${e.message}")
            -1
        }
    }

    // ---------------------------------------------------------
    // HAVERSINE DISTANCE
    // ---------------------------------------------------------
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) *
                Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    // ---------------------------------------------------------
    // RAW NOMINATIM
    // ---------------------------------------------------------
    private fun tryRawNominatim(lat: Double, lon: Double): String? {
        log("Nominatim RAW: querying")

        val url =
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&zoom=10&addressdetails=1"

        val body = fetchRaw(url)
        if (body != null) {
            return parseNominatim(body)
        }

        return null
    }

    private fun parseNominatim(body: String): String? {
        return try {
            val root = JSONObject(body)
            val addr = root.optJSONObject("address") ?: return null
            val code = addr.optString("country_code", "")
            if (code.isNotBlank()) code.lowercase(Locale.US) else null
        } catch (e: Exception) {
            log("Nominatim RAW parse error: ${e.message}")
            null
        }
    }

    // ---------------------------------------------------------
    // WEBVIEW NOMINATIM
    // ---------------------------------------------------------
    private fun tryWebNominatim(lat: Double, lon: Double): String? {
        log("Nominatim WEBVIEW: querying")

        val url =
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&zoom=10&addressdetails=1"

        var result: String? = null
        val latch = Object()

        web.fetch(url) { body ->
            result = if (body != null) parseNominatim(body) else null
            synchronized(latch) { latch.notify() }
        }

        synchronized(latch) { latch.wait(9000) }
        return result
    }

    // ---------------------------------------------------------
    // RAW HTTP HELPER
    // ---------------------------------------------------------
    private fun fetchRaw(urlString: String): String? {
        return try {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SpeedAlert/1.0")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            log("RAW fetch error: ${e.message}")
            null
        }
    }

    // ---------------------------------------------------------
    // LOGGING
    // ---------------------------------------------------------
    private fun log(msg: String) {
        val intent = Intent("speedalert.debug").apply {
            putExtra("msg", msg)
        }
        context.sendBroadcast(intent)
    }

    // ---------------------------------------------------------
    // DIAGNOSTICS HELPERS
    // ---------------------------------------------------------

    fun testRawOverpass(lat: Double, lon: Double): String {
        val query = buildOverpassQuery(lat, lon)

        for (server in overpassServers) {
            val url = "$server?data=$query"
            val body = fetchRaw(url)
            if (body != null) {
                val limit = parseOverpass(body, lat, lon)
                return "Server=$server  limit=$limit"
            }
        }
        return "RAW Overpass failed on all servers"
    }

    fun testWebOverpass(lat: Double, lon: Double): String {
        val query = buildOverpassQuery(lat, lon)

        for (server in overpassServers) {
            val url = "$server?data=$query"

            var result: Int? = null
            val latch = Object()

            web.fetch(url) { body ->
                result = if (body != null) parseOverpass(body, lat, lon) else -1
                synchronized(latch) { latch.notify() }
            }

            synchronized(latch) { latch.wait(9000) }

            if (result != null) {
                return "Server=$server  limit=$result"
            }
        }
        return "WebView Overpass failed on all servers"
    }

    fun testRawNominatim(lat: Double, lon: Double): String {
        val url =
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&zoom=10&addressdetails=1"

        val body = fetchRaw(url)
        return if (body != null) {
            "RAW Nominatim: ${parseNominatim(body)}"
        } else {
            "RAW Nominatim failed"
        }
    }

    fun testWebNominatim(lat: Double, lon: Double): String {
        val url =
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&zoom=10&addressdetails=1"

        var result: String? = null
        val latch = Object()

        web.fetch(url) { body ->
            result = if (body != null) parseNominatim(body) else null
            synchronized(latch) { latch.notify() }
        }

        synchronized(latch) { latch.wait(9000) }

        return "WebView Nominatim: ${result ?: "failed"}"
    }

    fun fetchRawOverpassResponse(lat: Double, lon: Double): String {
        val query = buildOverpassQuery(lat, lon)

        for (server in overpassServers) {
            val url = "$server?data=$query"
            val body = fetchRaw(url)
            if (body != null) {
                return "Server=$server\n\n$body"
            }
        }
        return "No Overpass server returned a response"
    }

}
