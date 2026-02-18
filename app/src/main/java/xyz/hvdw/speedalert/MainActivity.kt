package xyz.hvdw.speedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri

class MainActivity : AppCompatActivity() {

    private lateinit var txtSpeed: TextView
    private lateinit var txtLimit: TextView
    private lateinit var txtStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSettings: Button
    private lateinit var btnDebug: Button
    private lateinit var btnAbout: Button

    private lateinit var settings: SettingsManager
    private var defaultTextColor: Int = 0

    private val LOCATION_REQUEST = 1001
    private val BACKGROUND_LOCATION_REQUEST = 1002
    private val NOTIFICATION_REQUEST = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = SettingsManager(this)

        // ---------------------------------------------------------
        // UI ELEMENTS
        // ---------------------------------------------------------
        txtSpeed = findViewById(R.id.txtSpeed)
        txtLimit = findViewById(R.id.txtLimit)
        txtStatus = findViewById(R.id.txtStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnSettings = findViewById(R.id.btnSettings)
        btnDebug = findViewById(R.id.btnDebugScreen)
        btnAbout = findViewById(R.id.btnAbout)

        defaultTextColor = txtSpeed.currentTextColor

        // ---------------------------------------------------------
        // BUTTONS
        // ---------------------------------------------------------
        btnStart.setOnClickListener {
            startService(Intent(this, DrivingService::class.java))
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, DrivingService::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnDebug.setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }

        btnAbout.setOnClickListener { showAboutDialog() }

        // ---------------------------------------------------------
        // PERMISSIONS
        // ---------------------------------------------------------
        requestNotificationPermission()
        checkLocationPermission()
        checkBackgroundLocationPermission()
        requestBatteryOptimizationExemption()

        registerReceiver(speedReceiver, IntentFilter("xyz.hvdw.speedalert.SPEED_UPDATE"))

        // ---------------------------------------------------------
        // AUTO-START (corrected)
        // ---------------------------------------------------------
        maybeAutoStartService()
    }

    override fun onResume() {
        super.onResume()

        // Overlay permission check only — NO auto-start here
        if (!Settings.canDrawOverlays(this)) {
            txtStatus.text = getString(R.string.overlay_permission_missing)
            requestOverlayPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(speedReceiver)
    }

    // ---------------------------------------------------------
    // AUTO-START (corrected logic)
    // ---------------------------------------------------------
    private fun maybeAutoStartService() {
        val prefs = getSharedPreferences("speedalert_prefs", MODE_PRIVATE)
        val autoStart = prefs.getBoolean("auto_start_service", false)

        if (!autoStart) return

        // Only start if ALL permissions are granted
        if (!Settings.canDrawOverlays(this)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        startService(Intent(this, DrivingService::class.java))
    }

    // ---------------------------------------------------------
    // ABOUT DIALOG
    // ---------------------------------------------------------
    private fun showAboutDialog() {
        val version = BuildConfig.VERSION_NAME
        val author = "Harry van der Wolf (Surfer63)"
        val description = getString(R.string.about_description)
        val disclaimer_header = getString(R.string.disclaimer_header)
        val disclaimer = getString(R.string.disclaimer_body)

        val message = """
            SpeedAlert v$version
            $author

            $description

            $disclaimer_header
            $disclaimer
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_title))
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------------------------------------------------------
    // SPEED RECEIVER
    // ---------------------------------------------------------
    private val speedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val rawSpeed = intent?.getIntExtra("speed", -1) ?: -1
            val rawLimit = intent?.getIntExtra("limit", -1) ?: -1
            val overspeed = intent?.getBooleanExtra("overspeed", false) ?: false
            val acc = intent?.getFloatExtra("accuracy", -1f) ?: -1f

            val unit = settings.displayUnit()

            txtSpeed.text = if (rawSpeed >= 0)
                "${settings.convertSpeed(rawSpeed)} $unit"
            else "--"

            txtLimit.text = if (rawLimit > 0)
                "${settings.convertSpeed(rawLimit)} $unit"
            else "--"

            txtStatus.text = if (acc >= 0)
                getString(R.string.gps_accuracy, acc.toInt())
            else getString(R.string.gps_waiting)

            if (overspeed) {
                txtSpeed.setTextColor(Color.RED)
                txtLimit.setTextColor(Color.RED)
            } else {
                txtSpeed.setTextColor(defaultTextColor)
                txtLimit.setTextColor(defaultTextColor)
            }
        }
    }

    // ---------------------------------------------------------
    // PERMISSIONS
    // ---------------------------------------------------------
    private fun checkLocationPermission() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST
            )
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                    arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    BACKGROUND_LOCATION_REQUEST
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_REQUEST
                )
            }
        }
    }

    // ---------------------------------------------------------
    // BATTERY OPTIMIZATION EXEMPTION
    // ---------------------------------------------------------
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)

        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(
                        this,
                        "Please disable battery optimization manually",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ---------------------------------------------------------
    // OVERLAY PERMISSION
    // ---------------------------------------------------------
    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_overlay_title))
            .setMessage(getString(R.string.dialog_overlay_message))
            .setPositiveButton(getString(R.string.dialog_grant)) { _, _ -> openOverlaySettings() }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun openOverlaySettings() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (e: Exception) {
            showFytOverlayHelp()
        }
    }

    private fun showFytOverlayHelp() {
        AlertDialog.Builder(this)
            .setTitle("Overlay Permission (FYT)")
            .setMessage(
                "Your FYT head unit hides the overlay permission screen.\n\n" +
                        "To enable overlays:\n" +
                        "1. Go to Factory Settings\n" +
                        "2. Enter password: 3368 or 8888\n" +
                        "3. Find 'App permissions' or 'Overlay apps'\n" +
                        "4. Enable overlay for SpeedAlert\n\n" +
                        "If overlays still fail, reboot the unit."
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
