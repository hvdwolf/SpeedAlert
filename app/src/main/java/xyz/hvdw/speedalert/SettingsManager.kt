package xyz.hvdw.speedalert

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class SettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences("speed_settings", Context.MODE_PRIVATE)

    fun getOverspeedPercentage(): Int =
        prefs.getInt("overspeed_percentage", 5)   // default 5%

    fun setOverspeedPercentage(value: Int) =
        prefs.edit { putInt("overspeed_percentage", value) }

    fun getCustomSound(): Uri? =
        prefs.getString("custom_sound", null)?.let { Uri.parse(it) }

    fun setCustomSound(uri: Uri?) =
        prefs.edit { putString("custom_sound", uri?.toString()) }

    fun resetCustomSound() {
        prefs.edit().remove("custom_sound").apply()
    }

    // user can show/hide floating speedometer
    fun getShowSpeedometer(): Boolean =
        prefs.getBoolean("show_speedometer", false)

    fun setShowSpeedometer(enabled: Boolean) =
        prefs.edit { putBoolean("show_speedometer", enabled) }

    // semi transparent overlay (e.g., 60% alpha)
    fun getSemiTransparent(): Boolean =
        prefs.getBoolean("semi_transparent", false)

    fun setSemiTransparent(enabled: Boolean) =
        prefs.edit { putBoolean("semi_transparent", enabled) }

    fun setUseMph(enabled: Boolean) {
        prefs.edit().putBoolean("use_mph", enabled).apply()
    }

    fun getUseMph(): Boolean {
        return prefs.getBoolean("use_mph", false) // default = km/h
    }

}