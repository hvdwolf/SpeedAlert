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
    fun isOverspeedModePercentage(): Boolean =
        prefs.getBoolean("overspeed_mode_percentage", true)

    fun setOverspeedModePercentage(v: Boolean) {
        prefs.edit().putBoolean("overspeed_mode_percentage", v).apply()
    }

    // ---------------------------------------------------------
    // OVERSPEED VALUES
    // ---------------------------------------------------------
    fun getOverspeedPercentage(): Int =
        prefs.getInt("overspeed_pct", 10)

    fun setOverspeedPercentage(v: Int) {
        prefs.edit().putInt("overspeed_pct", v).apply()
    }

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
    // KMH / MPH (USER CONTROLLED)
    // ---------------------------------------------------------
    fun usesMph(): Boolean =
        prefs.getBoolean("use_mph", false)

    fun setUseMph(value: Boolean) {
        prefs.edit().putBoolean("use_mph", value).apply()
    }

    fun shouldConvertToMph(): Boolean =
        !countryUsesMph() && usesMph()

    fun shouldConvertToKmh(): Boolean =
        countryUsesMph() && !usesMph()

    fun displayUnit(): String {
        return if (usesMph()) "mph" else "km/h"
    }

    fun convertSpeed(value: Int): Int {
        return when {
            shouldConvertToMph() -> (value * 0.621371).toInt()
            shouldConvertToKmh() -> (value / 0.621371).toInt()
            else -> value
        }
    }

    // ---------------------------------------------------------
    // SOUND VOLUME
    // ---------------------------------------------------------
    fun getBeepVolume(): Float =
        prefs.getFloat("beep_volume", 1.0f)

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
    // COUNTRY IDENTIFICATION (STILL STORED, NOT USED FOR MPH)
    // ---------------------------------------------------------
    private val KEY_COUNTRY_CODE = "country_code"

    fun setCountryCode(code: String) {
        prefs.edit().putString(KEY_COUNTRY_CODE, code).apply()
    }

    fun getCountryCode(): String? =
        prefs.getString(KEY_COUNTRY_CODE, null)

    private val mphCountries = setOf(
        "US", // United States
        "GB", // United Kingdom
        "LR", // Liberia
        "MM", // Myanmar
        "BS", // Bahamas
        "BZ", // Belize
        "KY"  // Cayman Islands
    )

    fun countryUsesMph(): Boolean {
        val code = getCountryCode()?.uppercase() ?: return false
        return mphCountries.contains(code)
    }
}
