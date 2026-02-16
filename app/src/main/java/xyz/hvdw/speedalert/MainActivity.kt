package xyz.hvdw.speedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import xyz.hvdw.speedalert.BuildConfig


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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = SettingsManager(this)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    2001
                )
            }
        }

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

        val btnAbout = findViewById<Button>(R.id.btnAbout)
        btnAbout.setOnClickListener {
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
        // CHECK IF SERVICE SHOULD BE OPENED ON START APP
        // ---------------------------------------------------------
        //val prefs = getSharedPreferences("speedalert_prefs", MODE_PRIVATE)
        //val autoStart = prefs.getBoolean("auto_start_service", false)
  
        //if (autoStart) {
        //    startService(Intent(this, DrivingService::class.java))
        //}


        // ---------------------------------------------------------
        // PERMISSIONS + RECEIVERS
        // ---------------------------------------------------------
        checkLocationPermission()
        registerReceiver(speedReceiver, IntentFilter("xyz.hvdw.speedalert.SPEED_UPDATE"))
    }

    override fun onResume() {
        super.onResume()

        // 1. Check overlay permission first
        if (!Settings.canDrawOverlays(this)) {
            txtStatus.text = getString(R.string.overlay_permission_missing)
            requestOverlayPermission()
            return
        }

        // 2. Check POST_NOTIFICATIONS permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

                // Do NOT start the service yet
                return
            }
        }

        // 3. Check location permission
        checkLocationPermission()

        // 4. Auto-start service ONLY when everything is allowed
        val prefs = getSharedPreferences("speedalert_prefs", MODE_PRIVATE)
        val autoStart = prefs.getBoolean("auto_start_service", false)

        if (autoStart) {
            startService(Intent(this, DrivingService::class.java))
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(speedReceiver)
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

            // -----------------------------
            // GPS SPEED (converted if needed)
            // -----------------------------
            if (rawSpeed >= 0) {
                val displaySpeed = settings.convertSpeed(rawSpeed)
                txtSpeed.text = "$displaySpeed $unit"
            } else {
                txtSpeed.text = "--"
            }

            // -----------------------------
            // ROAD LIMIT (converted if needed)
            // -----------------------------
            if (rawLimit > 0) {
                val displayLimit = settings.convertSpeed(rawLimit)
                txtLimit.text = "$displayLimit $unit"
            } else {
                txtLimit.text = "--"
            }

            // -----------------------------
            // GPS ACCURACY
            // -----------------------------
            if (acc >= 0) {
                txtStatus.text = getString(R.string.gps_accuracy, acc.toInt())
            } else {
                txtStatus.text = getString(R.string.gps_waiting)
            }

            // -----------------------------
            // COLORING
            // -----------------------------
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.gps_permission_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.gps_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

        if (requestCode == 2001) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notifications disabled — service may not run", Toast.LENGTH_LONG).show()
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
                android.net.Uri.parse("package:$packageName")
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
