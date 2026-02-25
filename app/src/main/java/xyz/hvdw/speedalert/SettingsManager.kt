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

    fun useSignOverlay(): Boolean {
        return prefs.getBoolean("use_sign_overlay", false)
    }

    fun setUseSignOverlay(value: Boolean) {
        prefs.edit().putBoolean("use_sign_overlay", value).apply()
    }

    fun setMinimizeOnStart(value: Boolean) {
        prefs.edit().putBoolean("minimize_on_start", value).apply()
    }

    fun getMinimizeOnStart(): Boolean {
        return prefs.getBoolean("minimize_on_start", false)
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

    fun useCountryFallback(): Boolean {
        return prefs.getBoolean("swuse_country_fallback", true)
    }

    fun setUseCountryFallback(value: Boolean) {
        prefs.edit().putBoolean("swuse_country_fallback", value).apply()
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

    // ---------------------------------------------------------
    // Get/set speed limit retrieval thresholds
    // ---------------------------------------------------------
    fun getSpeedLimitFetchIntervalMs(): Long {
        return prefs.getLong("speed_limit_fetch_interval", 4000L)
    }

    fun setSpeedLimitFetchIntervalMs(value: Long) {
        prefs.edit().putLong("speed_limit_fetch_interval", value).apply()
    }

    fun getMinDistanceForFetch(): Float {
        return prefs.getFloat("min_distance_fetch", 10f)
    }

    fun setMinDistanceForFetch(value: Float) {
        prefs.edit().putFloat("min_distance_fetch", value).apply()
    }


}
