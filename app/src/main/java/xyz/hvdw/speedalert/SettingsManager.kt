package xyz.hvdw.speedalert

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class SettingsManager(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // -----------------------------
    // SPEEDOMETER OVERLAY
    // -----------------------------
    fun getShowSpeedometer(): Boolean =
        prefs.getBoolean("show_speedometer", true)

    fun setShowSpeedometer(v: Boolean) =
        prefs.edit { putBoolean("show_speedometer", v) }

    fun getSemiTransparent(): Boolean =
        prefs.getBoolean("semi_transparent", false)

    fun setSemiTransparent(v: Boolean) =
        prefs.edit { putBoolean("semi_transparent", v) }

    // -----------------------------
    // UNITS
    // -----------------------------
    fun getUseMph(): Boolean =
        prefs.getBoolean("use_mph", false)

    fun setUseMph(v: Boolean) =
        prefs.edit { putBoolean("use_mph", v) }

    // -----------------------------
    // OVERSPEED
    // -----------------------------
    fun getOverspeedPercentage(): Int =
        prefs.getInt("overspeed_percent", 5)

    fun setOverspeedPercentage(v: Int) =
        prefs.edit { putInt("overspeed_percent", v) }

    // -----------------------------
    // BROADCAST METADATA
    // -----------------------------
    fun isBroadcastEnabled(): Boolean =
        prefs.getBoolean("broadcast_enabled", false)

    fun setBroadcastEnabled(v: Boolean) =
        prefs.edit { putBoolean("broadcast_enabled", v) }

    // -----------------------------
    // CUSTOM SOUND
    // -----------------------------
    fun getCustomSound(): Uri? {
        val s = prefs.getString("custom_sound", null) ?: return null
        return Uri.parse(s)
    }

    fun setCustomSound(uri: Uri) =
        prefs.edit { putString("custom_sound", uri.toString()) }
}
