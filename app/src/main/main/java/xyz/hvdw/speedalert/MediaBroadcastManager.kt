package xyz.hvdw.speedalert

import android.content.Context
import android.content.Intent

class MediaBroadcastManager(private val context: Context) {

    fun start() {
        // No-op for simple version
    }

    fun stop() {
        // No-op
    }

    fun updateMetadata(appName: String, speed: Int, limit: Int, useMph: Boolean) {
        val intent = Intent("xyz.hvdw.speedalert.MEDIA_UPDATE").apply {
            putExtra("app", appName)
            putExtra("speed", speed)
            putExtra("limit", limit)
            putExtra("mph", useMph)
        }
        context.sendBroadcast(intent)
    }
}
