package xyz.hvdw.speedalert

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class DebugActivity : AppCompatActivity() {

    private lateinit var edtLat: EditText
    private lateinit var edtLon: EditText
    private lateinit var txtDebugFull: TextView
    private lateinit var scrollDebugFull: ScrollView

    private val logBuffer = StringBuilder()
    private var lastUiUpdate = 0L

    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val msg = intent?.getStringExtra("msg") ?: return
            append(msg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        edtLat = findViewById(R.id.edtLat)
        edtLon = findViewById(R.id.edtLon)

        txtDebugFull = findViewById(R.id.txtDebugFull)
        scrollDebugFull = findViewById(R.id.scrollDebugFull)

        registerReceiver(debugReceiver, IntentFilter("speedalert.debug"))

        // Load logfile
        val file = File(filesDir, "speedalert.log")
        if (file.exists()) {
            txtDebugFull.text = file.readText()
            scrollToBottom()
        } else {
            txtDebugFull.text = "Logfile does not exist yet."
        }

        findViewById<Button>(R.id.btnCheckOverlay).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, getString(R.string.overlay_permission_ok), Toast.LENGTH_SHORT).show()
            } else {
                requestOverlayPermission()
            }
        }

        findViewById<Button>(R.id.btnTestLookup).setOnClickListener {
            val lat = edtLat.text.toString().toDoubleOrNull()
            val lon = edtLon.text.toString().toDoubleOrNull()

            if (lat == null || lon == null) {
                Toast.makeText(this, getString(R.string.enter_valid_latlon), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val repo = SpeedLimitRepository(applicationContext)

            Thread {
                try {
                    logToFile("Calling repo.getSpeedLimit($lat, $lon)")
                    //val radius = dynamicRadius(acc)
                    val radius = 20
                    val result = repo.getSpeedLimit(lat, lon, radius)
                    runOnUiThread {
                        val msg = getString(R.string.test_lookup_result, result.toString(), lat, lon)
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        logToFile(msg)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        logToFile("Lookup failed: ${e.message}")
                        Toast.makeText(this, "Lookup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()

        }

        findViewById<Button>(R.id.btnCopyLogFull).setOnClickListener {
            (application as SpeedAlertApp).copyLogfile(this)
        }

        findViewById<Button>(R.id.btnShareLogFull).setOnClickListener {
            (application as SpeedAlertApp).shareLogfile(this)
        }

        findViewById<Button>(R.id.btnEmptyLogFull).setOnClickListener {
            (application as SpeedAlertApp).emptyLogfile(this)
            txtDebugFull.text = ""
            scrollToBottom()
        }

        findViewById<Button>(R.id.btnShowSettings).setOnClickListener {
            dumpAllSettings()
        }

    }

    private fun append(msg: String, forceFlush: Boolean = false) {
        // Add to buffer
        logBuffer.append(msg).append('\n')

        // Throttle UI updates to once per second
        val now = System.currentTimeMillis()
        if (!forceFlush && now - lastUiUpdate < 1000) return
        lastUiUpdate = now

        // Push buffered text to UI
        txtDebugFull.text = logBuffer.toString()

        // Scroll down
        scrollDebugFull.post {
            scrollDebugFull.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(debugReceiver)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun checkOverlayPermission() {
        // your existing logic
    }

    //private fun testSpeedLimitLookup(lat: Double?, lon: Double?) {
        // your existing logic
    //}

    private fun scrollToBottom() {
        scrollDebugFull.post {
            scrollDebugFull.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun logToFile(text: String) {
        try {
            val file = File(filesDir, "speedalert.log")
            file.appendText("$text\n")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to write log: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dynamicRadius(acc: Float): Int {
        return when {
            acc <= 2.5f -> 10
            acc <= 5f   -> 15
            acc <= 10f  -> 20
            else        -> 25
        }
    }

    private fun dumpAllSettings() {
        append("=== SETTINGS DUMP START ===")

        // 1. Dump raw SharedPreferences
        append("-- SharedPreferences (speedalert_settings) --")
        val prefs = getSharedPreferences("speedalert_settings", Context.MODE_PRIVATE)
        for ((key, value) in prefs.all) {
            append("$key = $value")
        }

        // 2. Dump SettingsManager values
        append("-- SettingsManager values --")
        val sm = SettingsManager(this)

        append("show_speedometer = ${sm.getShowSpeedometer()}")
        append("use_sign_overlay = ${sm.useSignOverlay()}")
        append("hide_current_speed = ${sm.hideCurrentSpeed()}")
        append("minimize_on_start = ${sm.getMinimizeOnStart()}")

        append("overspeed_mode_percentage = ${sm.isOverspeedModePercentage()}")
        append("overspeed_pct = ${sm.getOverspeedPercentage()}")
        append("overspeed_fixed_kmh = ${sm.getOverspeedFixedKmh()}")

        append("use_country_fallback = ${sm.useCountryFallback()}")

        append("broadcast_enabled = ${sm.isBroadcastEnabled()}")

        append("use_mph = ${sm.usesMph()}")
        append("display_unit = ${sm.displayUnit()}")
        append("should_convert_to_mph = ${sm.shouldConvertToMph()}")
        append("should_convert_to_kmh = ${sm.shouldConvertToKmh()}")
        append("country_code = ${sm.getCountryCode()}")
        append("country_uses_mph = ${sm.countryUsesMph()}")

        append("beep_volume = ${sm.getBeepVolume()}")

        append("custom_sound = ${sm.getCustomSound()?.toString() ?: "null"}")

        append("overlay_x = ${sm.getOverlayX()}")
        append("overlay_y = ${sm.getOverlayY()}")
        append("overlay_alpha = ${prefs.getInt("overlay_alpha", -1)}")
        append("speedo_brightness = ${prefs.getInt("speedo_brightness", -1)}")
        append("overlay_text_scale = ${prefs.getFloat("overlay_text_scale", -1f)}")

        append("speed_limit_fetch_interval = ${sm.getSpeedLimitFetchIntervalMs()}")
        append("min_distance_fetch = ${sm.getMinDistanceForFetch()}")

        append("mute_beep = ${sm.isMuted()}")

        append("=== SETTINGS DUMP END ===", forceFlush = true)
    }


}
