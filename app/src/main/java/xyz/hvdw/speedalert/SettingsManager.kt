package xyz.hvdw.speedalert

import android.content.Context
import android.net.Uri

class SettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences("speedalert_settings", Context.MODE_PRIVATE)

    fun getShowSpeedometer(): Boolean =
        prefs.getBoolean("show_speedometer", true)

    fun setShowSpeedometer(v: Boolean) {
        prefs.edit().putBoolean("show_speedometer", v).apply()
    }

    fun isBroadcastEnabled(): Boolean =
        prefs.getBoolean("broadcast_enabled", true)

    fun setBroadcastEnabled(v: Boolean) {
        prefs.edit().putBoolean("broadcast_enabled", v).apply()
    }

    fun getUseMph(): Boolean =
        prefs.getBoolean("use_mph", false)

    fun setUseMph(v: Boolean) {
        prefs.edit().putBoolean("use_mph", v).apply()
    }

    fun getOverspeedPercentage(): Int =
        prefs.getInt("overspeed_pct", 10)

    fun setOverspeedPercentage(v: Int) {
        prefs.edit().putInt("overspeed_pct", v).apply()
    }

    fun getCustomSound(): Uri? {
        val s = prefs.getString("custom_sound", null) ?: return null
        return Uri.parse(s)
    }

    fun setCustomSound(uri: Uri?) {
        prefs.edit().putString("custom_sound", uri?.toString()).apply()
    }

    fun getOverlayX(): Int {
        return prefs.getInt("overlay_x", 50)
    }

    fun getOverlayY(): Int {
        return prefs.getInt("overlay_y", 50)
    }

    fun setOverlayX(x: Int) {
        prefs.edit().putInt("overlay_x", x).apply()
    }

    fun setOverlayY(y: Int) {
        prefs.edit().putInt("overlay_y", y).apply()
    }

}
