package xyz.hvdw.speedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var txtSpeed: TextView
    private lateinit var txtLimit: TextView
    private lateinit var txtStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var swShowOverlay: Switch
    private lateinit var swBroadcast: Switch
    private lateinit var edtLat: EditText
    private lateinit var edtLon: EditText
    private lateinit var seekOverspeed: SeekBar
    private lateinit var txtOverspeedLabel: TextView

    private val LOCATION_REQUEST = 1001
    private var defaultTextColor: Int = 0

    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = SettingsManager(this)

        txtSpeed = findViewById(R.id.txtSpeed)
        txtLimit = findViewById(R.id.txtLimit)
        txtStatus = findViewById(R.id.txtStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        swShowOverlay = findViewById(R.id.swShowOverlay)
        swBroadcast = findViewById(R.id.swBroadcast)
        edtLat = findViewById(R.id.edtLat)
        edtLon = findViewById(R.id.edtLon)
        seekOverspeed = findViewById(R.id.seekOverspeed)
        txtOverspeedLabel = findViewById(R.id.txtOverspeedLabel)

        defaultTextColor = txtSpeed.currentTextColor

        // -----------------------------
        // OVERSPEED SLIDER
        // -----------------------------
        val savedTolerance = settings.getOverspeedTolerance()
        seekOverspeed.progress = savedTolerance
        txtOverspeedLabel.text = "Overspeed tolerance: $savedTolerance%"

        seekOverspeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                txtOverspeedLabel.text = "Overspeed tolerance: $value%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = seekOverspeed.progress
                settings.setOverspeedTolerance(value)
                Toast.makeText(this@MainActivity, "Saved overspeed percentage: $value%", Toast.LENGTH_SHORT).show()
            }
        })

        // -----------------------------
        // BUTTONS
        // -----------------------------
        findViewById<Button>(R.id.btnCheckOverlay).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission OK", Toast.LENGTH_SHORT).show()
            } else {
                requestOverlayPermission()
            }
        }

        findViewById<Button>(R.id.btnDebugScreen).setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }

        findViewById<Button>(R.id.btnTestLookup).setOnClickListener {
            val lat = edtLat.text.toString().toDoubleOrNull()
            val lon = edtLon.text.toString().toDoubleOrNull()

            if (lat == null || lon == null) {
                Toast.makeText(this, "Enter valid latitude and longitude", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val repo = SpeedLimitRepository(this)

            Thread {
                val result = repo.getSpeedLimit(lat, lon)
                runOnUiThread {
                    val msg = "Test lookup: $result at $lat,$lon"
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    logToFile(msg)
                }
            }.start()
        }

        findViewById<Button>(R.id.btnDiagnostics).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        // -----------------------------
        // SWITCHES
        // -----------------------------
        swShowOverlay.isChecked = settings.getShowSpeedometer()
        swBroadcast.isChecked = settings.isBroadcastEnabled()

        swShowOverlay.setOnCheckedChangeListener { _, checked ->
            settings.setShowSpeedometer(checked)
        }

        swBroadcast.setOnCheckedChangeListener { _, checked ->
            settings.setBroadcastEnabled(checked)
        }

        // -----------------------------
        // SERVICE START/STOP
        // -----------------------------
        btnStart.setOnClickListener {
            startService(Intent(this, DrivingService::class.java))
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, DrivingService::class.java))
        }

        // -----------------------------
        // PERMISSIONS + RECEIVERS
        // -----------------------------
        checkLocationPermission()

        registerReceiver(speedReceiver, IntentFilter("xyz.hvdw.speedalert.SPEED_UPDATE"))
    }

    override fun onResume() {
        super.onResume()

        if (!Settings.canDrawOverlays(this)) {
            txtStatus.text = "Overlay permission missing"
            requestOverlayPermission()
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
            val speed = intent?.getIntExtra("speed", -1) ?: -1
            val limit = intent?.getIntExtra("limit", -1) ?: -1
            val overspeed = intent?.getBooleanExtra("overspeed", false) ?: false
            val acc = intent?.getFloatExtra("accuracy", -1f) ?: -1f

            logToFile("Receiver: speed=$speed, limit=$limit, acc=$acc")

            txtSpeed.text = if (speed >= 0) getString(R.string.speed_value, speed) else "--"
            txtLimit.text = if (limit > 0) getString(R.string.limit_value, limit) else "--"
            txtStatus.text = if (acc >= 0) "GPS accuracy: ${acc.toInt()} m" else "Waiting for GPS…"

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
    // OVERLAY PERMISSION
    // ---------------------------------------------------------
    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return

        AlertDialog.Builder(this)
            .setTitle("Overlay Permission Required")
            .setMessage("SpeedAlert needs permission to display the floating speedometer.")
            .setPositiveButton("Grant") { _, _ ->
                openOverlaySettings()
            }
            .setNegativeButton("Cancel", null)
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

    // ---------------------------------------------------------
    // LOGGING
    // ---------------------------------------------------------
    private fun logToFile(msg: String) {
        try {
            val file = File(filesDir, "speedalert.log")
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(System.currentTimeMillis())
            file.appendText("[$ts] $msg\n")
        } catch (_: Exception) {}
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
                Toast.makeText(this, "GPS permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "GPS permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
