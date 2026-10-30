package xyz.hvdw.speedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.net.URL
import java.net.HttpURLConnection
import org.json.JSONObject
import android.util.Log
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {

    private lateinit var txtSpeed: TextView
    private lateinit var txtLimit: TextView
    private lateinit var txtStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnStartGpsRecording: Button
    private lateinit var btnStopGpsRecording: Button
    private lateinit var txtGpsRecordingStatus: TextView
    private lateinit var txtGpsRecordingPoints: TextView
    private lateinit var btnSettings: Button
    private lateinit var btnDebug: Button
    private lateinit var btnAbout: Button

    private lateinit var settings: SettingsManager
    private var defaultTextColor: Int = 0

    private val LOCATION_REQUEST = 1001
    private val BACKGROUND_LOCATION_REQUEST = 1002
    private val NOTIFICATION_REQUEST = 2001

    private var gpsRecordingReceiverRegistered = false

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
        btnAbout.bringToFront()
        btnStartGpsRecording = findViewById(R.id.btnStartGpsRecording)
        btnStopGpsRecording = findViewById(R.id.btnStopGpsRecording)
        txtGpsRecordingStatus = findViewById(R.id.txtGpsRecordingStatus)
        txtGpsRecordingPoints = findViewById(R.id.txtGpsRecordingPoints)


        defaultTextColor = txtSpeed.currentTextColor

        // ---------------------------------------------------------
        // BUTTONS
        // ---------------------------------------------------------
        btnStart.setOnClickListener {
            //startService(Intent(this, DrivingService::class.java))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, DrivingService::class.java))
            } else {
                startService(Intent(this, DrivingService::class.java))
            }
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

        btnStartGpsRecording.setOnClickListener {
            val intent = Intent(this, DrivingService::class.java).apply {
                action = DrivingService.ACTION_START_GPS_RECORDING
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            //txtGpsRecordingStatus.setText(R.string.gps_recording_active)
        }

        btnStopGpsRecording.setOnClickListener {
            val intent = Intent(this, DrivingService::class.java).apply {
                action = DrivingService.ACTION_STOP_GPS_RECORDING
            }
            //txtGpsRecordingStatus.setText(R.string.gps_recording_stopped)
            startService(intent)
        }

        btnAbout.setOnClickListener {
            Log.e("ABOUT", "CLICKED")
            Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show()
            showAboutDialog() }

        // ---------------------------------------------------------
        // PERMISSIONS
        // ---------------------------------------------------------
        requestNotificationPermission()
        checkLocationPermission()
        checkBackgroundLocationPermission()
        requestBatteryOptimizationExemption()

        registerReceiver(speedReceiver, IntentFilter("xyz.hvdw.speedalert.SPEED_UPDATE"))

        val gpsRecordingFilter = IntentFilter(
            DrivingService.ACTION_GPS_RECORDING_STATUS
        )

        ContextCompat.registerReceiver(
            this,
            gpsRecordingReceiver,
            gpsRecordingFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        gpsRecordingReceiverRegistered = true

        // Check for new version
        checkForNewVersion()

        // ---------------------------------------------------------
        // AUTO-START + MINIMIZE
        // ---------------------------------------------------------
        maybeAutoStartService()
    }

    private val gpsRecordingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DrivingService.ACTION_GPS_RECORDING_STATUS) return

                val recording = intent.getBooleanExtra(
                    DrivingService.EXTRA_GPS_RECORDING, false
                )

                val fileName = intent.getStringExtra(
                    DrivingService.EXTRA_GPS_RECORDING_FILE
                )

                val points = intent.getIntExtra(
                    DrivingService.EXTRA_GPS_RECORDING_POINTS, 0
                )

                runOnUiThread {
                    updateGpsRecordingUi(recording, fileName, points)
                }
        }
    }

    private fun updateGpsRecordingUi(
        recording: Boolean,
        fileName: String? = null,
        points: Int = 0
    ) {
        btnStartGpsRecording.isEnabled = !recording
        btnStopGpsRecording.isEnabled = recording

        txtGpsRecordingStatus.text = if (recording) {
            if (fileName != null) {
                "${getString(R.string.gps_recording_active)}: $fileName"
            } else {
                getString(R.string.gps_recording_active)
            }
        } else {
            getString(R.string.gps_recording_stopped)
        }

        txtGpsRecordingPoints.text = if (recording) {
            getString(R.string.gps_recording_points, points)
        } else {
            getString(R.string.gps_recording_points_saved, points)
        }
    }


    override fun onResume() {
        super.onResume()

        // Overlay permission check only — NO auto-start here
        if (!Settings.canDrawOverlays(this)) {
            txtStatus.text = getString(R.string.overlay_permission_missing)
            requestOverlayPermission()
        }

        restoreGpsRecordingStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (gpsRecordingReceiverRegistered) {
            unregisterReceiver(gpsRecordingReceiver)
            gpsRecordingReceiverRegistered = false
        }
        unregisterReceiver(speedReceiver)
    }

    // ---------------------------------------------------------
    // AUTO-START + MINIMIZE LOGIC
    // ---------------------------------------------------------
    private fun maybeAutoStartService() {
        val prefs = getSharedPreferences("speedalert_prefs", MODE_PRIVATE)
        val autoStart = prefs.getBoolean("auto_start_service", false)

        val minimize = settings.getMinimizeOnStart()

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

        // request external storage for non head unit devices
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                3001
            )
        }


        // Start the service
        //startService(Intent(this, DrivingService::class.java))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, DrivingService::class.java))
        } else {
            startService(Intent(this, DrivingService::class.java))
        }

        // Minimize if user enabled it
        if (minimize) {
            moveTaskToBack(true)
        }
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

/*        val message = """
            SpeedAlert v$version
            $author

            $description

            $disclaimer_header
            $disclaimer
        """.trimIndent() */
        val message =
            "SpeedAlert v$version\n" +
            "$author\n\n" +
            "$description\n\n" +
            "$disclaimer_header\n" +
            "$disclaimer"


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
                //getString(R.string.gps_accuracy, acc.roundToInt())
                getString(R.string.gps_accuracy, acc)
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

   private fun checkForNewVersion() {
        Thread {
            try {
                val url = URL("https://api.github.com/repos/hvdwolf/SpeedAlert/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "SpeedAlert")

                val json = conn.inputStream.bufferedReader().readText()
                val obj = JSONObject(json)

                if (!obj.has("tag_name")) {
                    Log.e("UpdateCheck", "GitHub response missing tag_name: $json")
                    return@Thread
                }

                val latestTag = obj.getString("tag_name").trim()
                val latestClean = latestTag.removePrefix("v").removePrefix("V")

                val currentVersion = BuildConfig.VERSION_NAME.trim()

                if (isNewerVersion(latestClean, currentVersion)) {
                    runOnUiThread {
                        showUpdateDialog(latestClean)
                    }
                }

            } catch (e: Exception) {
                Log.e("UpdateCheck", "Failed to check update", e)
            }
        }.start()
    }


    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".")
        val currentParts = current.split(".")

        val maxLen = maxOf(latestParts.size, currentParts.size)

        for (i in 0 until maxLen) {
            val l = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
            val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0

            if (l > c) return true
            if (l < c) return false
        }
        return false
    }


    private fun showUpdateDialog(latest: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available_title))
            .setMessage(getString(R.string.update_available_message, latest))
            .setPositiveButton(getString(R.string.update_available_open)) { _, _ ->
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/hvdwolf/SpeedAlert/releases/latest")
                )
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.update_available_cancel), null)
            .show()
    }


    private fun restoreGpsRecordingStatus() {
        val prefs = getSharedPreferences(
            "gps_recording_state",
            Context.MODE_PRIVATE
        )

        val recording = prefs.getBoolean("recording", false)
        val points = prefs.getInt("points", 0)
        val fileName = prefs.getString("file_name", null)

        updateGpsRecordingUi(recording, fileName, points)
    }


}
