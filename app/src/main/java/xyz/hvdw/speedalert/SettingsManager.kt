package xyz.hvdw.speedalert

import android.content.Context
import android.net.Uri

class SettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences("speedalert_settings", Context.MODE_PRIVATE)

    // ---------------------------------------------------------
    // SPEEDOMETER OVERLAY
    // ---------------------------------------------------------
    fun getShowSpeedometer(): Boolean =
        prefs.getBoolean("show_speedometer", true)

    fun setShowSpeedometer(v: Boolean) {
        prefs.edit().putBoolean("show_speedometer", v).apply()
    }

    // ---------------------------------------------------------
    // OVERSPEED MODE
    // ---------------------------------------------------------
    // true  = percentage mode
    // false = fixed km/h (or mph) mode
    fun isOverspeedModePercentage(): Boolean =
        prefs.getBoolean("overspeed_mode_percentage", true)

    fun setOverspeedModePercentage(v: Boolean) {
        prefs.edit().putBoolean("overspeed_mode_percentage", v).apply()
    }

    // ---------------------------------------------------------
    // OVERSPEED VALUES
    // ---------------------------------------------------------
    // Percentage (existing)
    fun getOverspeedPercentage(): Int =
        prefs.getInt("overspeed_pct", 10)

    fun setOverspeedPercentage(v: Int) {
        prefs.edit().putInt("overspeed_pct", v).apply()
    }

    // Fixed km/h (new)
    fun getOverspeedFixedKmh(): Int =
        prefs.getInt("overspeed_fixed_kmh", 5)

    fun setOverspeedFixedKmh(v: Int) {
        prefs.edit().putInt("overspeed_fixed_kmh", v).apply()
    }

    // ---------------------------------------------------------
    // BROADCAST
    // ---------------------------------------------------------
    fun isBroadcastEnabled(): Boolean =
        prefs.getBoolean("broadcast_enabled", true)

    fun setBroadcastEnabled(v: Boolean) {
        prefs.edit().putBoolean("broadcast_enabled", v).apply()
    }


    // ---------------------------------------------------------
    // SOUND VOLUME
    // ---------------------------------------------------------
    fun getBeepVolume(): Float =
        prefs.getFloat("beep_volume", 1.0f)   // default: full volume

    fun setBeepVolume(v: Float) {
        prefs.edit().putFloat("beep_volume", v).apply()
    }

    // ---------------------------------------------------------
    // CUSTOM SOUND
    // ---------------------------------------------------------
    fun getCustomSound(): Uri? {
        val s = prefs.getString("custom_sound", null) ?: return null
        return Uri.parse(s)
    }

    fun setCustomSound(uri: Uri?) {
        prefs.edit().putString("custom_sound", uri?.toString()).apply()
    }

    // ---------------------------------------------------------
    // OVERLAY POSITION
    // ---------------------------------------------------------
    fun getOverlayX(): Int =
        prefs.getInt("overlay_x", 50)

    fun getOverlayY(): Int =
        prefs.getInt("overlay_y", 50)

    fun setOverlayX(x: Int) {
        prefs.edit().putInt("overlay_x", x).apply()
    }

    fun setOverlayY(y: Int) {
        prefs.edit().putInt("overlay_y", y).apply()
    }

    // ---------------------------------------------------------
    // COUNTRY IDENTIFICATION
    // ---------------------------------------------------------

    private val KEY_COUNTRY_CODE = "country_code"

    fun setCountryCode(code: String) {
        prefs.edit().putString(KEY_COUNTRY_CODE, code).apply()
    }

    fun getCountryCode(): String? {
        return prefs.getString(KEY_COUNTRY_CODE, null)
    }

    private val mphCountries = setOf(
        "US", // United States
        "GB", // United Kingdom
        "LR", // Liberia
        "MM", // Myanmar
        "BS", // Bahamas
        "BZ", // Belize
        "KY"  // Cayman Islands
    )

    fun usesMph(): Boolean {
        val code = getCountryCode()?.uppercase() ?: return false
        return mphCountries.contains(code)
    }

}
