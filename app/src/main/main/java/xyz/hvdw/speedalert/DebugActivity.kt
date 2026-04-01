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
        fun out(msg: String) {
            append(msg)
            logToFile(msg)   // <-- write to log file too
        }

        out("=== SETTINGS DUMP START ===")

        // 1. Dump raw SharedPreferences
        out("-- SharedPreferences (speedalert_settings) --")
        val prefs = getSharedPreferences("speedalert_settings", Context.MODE_PRIVATE)
        for ((key, value) in prefs.all) {
            out("$key = $value")
        }

        // 2. Dump SettingsManager values
        out("-- SettingsManager values --")
        val sm = SettingsManager(this)

        out("show_speedometer = ${sm.getShowSpeedometer()}")
        out("use_sign_overlay = ${sm.useSignOverlay()}")
        out("hide_current_speed = ${sm.hideCurrentSpeed()}")
        out("minimize_on_start = ${sm.getMinimizeOnStart()}")

        out("overspeed_mode_percentage = ${sm.isOverspeedModePercentage()}")
        out("overspeed_pct = ${sm.getOverspeedPercentage()}")
        out("overspeed_fixed_kmh = ${sm.getOverspeedFixedKmh()}")

        out("use_country_fallback = ${sm.useCountryFallback()}")

        out("broadcast_enabled = ${sm.isBroadcastEnabled()}")

        out("use_mph = ${sm.usesMph()}")
        out("display_unit = ${sm.displayUnit()}")
        out("should_convert_to_mph = ${sm.shouldConvertToMph()}")
        out("should_convert_to_kmh = ${sm.shouldConvertToKmh()}")
        out("country_code = ${sm.getCountryCode()}")
        out("country_uses_mph = ${sm.countryUsesMph()}")

        out("speed_limit_db_name = ${sm.getSpeedLimitDbName()}")
        out("camera_db_name = ${sm.getCameraDbName()}")

        out("beep_volume = ${sm.getBeepVolume()}")

        out("custom_sound = ${sm.getCustomSound()?.toString() ?: "null"}")

        out("overlay_x = ${sm.getOverlayX()}")
        out("overlay_y = ${sm.getOverlayY()}")
        out("overlay_alpha = ${prefs.getInt("overlay_alpha", -1)}")
        out("speedo_brightness = ${prefs.getInt("speedo_brightness", -1)}")
        out("overlay_text_scale = ${prefs.getFloat("overlay_text_scale", -1f)}")

        out("speed_limit_fetch_interval = ${sm.getSpeedLimitFetchIntervalMs()}")
        out("min_distance_fetch = ${sm.getMinDistanceForFetch()}")

        out("mute_beep = ${sm.isMuted()}")

        out("=== SETTINGS DUMP END ===")
        append("", forceFlush = true)
    }

}
