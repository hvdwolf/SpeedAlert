package xyz.hvdw.speedalert

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SpeedLimitRepository {

    private val client = OkHttpClient()

    suspend fun getSpeedLimit(lat: Double, lon: Double): Int? = withContext(Dispatchers.IO) {
        try {
            val query = """
                [out:json][timeout:10];
                way(around:30,$lat,$lon)
                  ["highway"]
                  ["maxspeed"];
                out tags;
            """.trimIndent()

            val body = query.toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val req = Request.Builder()
                .url("https://overpass-api.de/api/interpreter")
                .post(body)
                .build()

            val res = client.newCall(req).execute().body?.string() ?: return@withContext null
            val json = JSONObject(res)
            val elems = json.optJSONArray("elements") ?: return@withContext null
            if (elems.length() == 0) return@withContext null

            val tags = elems.getJSONObject(0).optJSONObject("tags") ?: return@withContext null

            // FIX: optString fallback must be a String, not null
            val speedStr = tags.optString("maxspeed", "")

            if (speedStr.isBlank()) return@withContext null

            // Extract digits only (handles "50 mph", "80;70", "signals", etc.)
            speedStr.replace("""\D""".toRegex(), "").toIntOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
