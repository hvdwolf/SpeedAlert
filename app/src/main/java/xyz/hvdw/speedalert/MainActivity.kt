package xyz.hvdw.speedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var txtSpeed: TextView
    private lateinit var txtLimit: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtDebug: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var swShowOverlay: Switch
    private lateinit var swBroadcast: Switch
    private lateinit var edtLat: EditText
    private lateinit var edtLon: EditText
    private lateinit var scrollDebug: ScrollView

    private val LOCATION_REQUEST = 1001
    private var defaultTextColor: Int = 0

    //private lateinit var locationManager: LocationManager
    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = SettingsManager(this)

        txtSpeed = findViewById(R.id.txtSpeed)
        txtLimit = findViewById(R.id.txtLimit)
        txtStatus = findViewById(R.id.txtStatus)
        txtDebug = findViewById(R.id.txtDebug)
        scrollDebug = findViewById(R.id.scrollDebug)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        swShowOverlay = findViewById(R.id.swShowOverlay)
        swBroadcast = findViewById(R.id.swBroadcast)
        edtLat = findViewById(R.id.edtLat)
        edtLon = findViewById(R.id.edtLon)

        defaultTextColor = txtSpeed.currentTextColor

        findViewById<Button>(R.id.btnCheckOverlay).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission OK", Toast.LENGTH_SHORT).show()
            } else {
                requestOverlayPermission()
            }
        }

        findViewById<Button>(R.id.btnCopyLog).setOnClickListener {
            copyLogfile()
        }

        findViewById<Button>(R.id.btnShareLog).setOnClickListener {
            shareLogfile()
        }

        findViewById<Button>(R.id.btnEmptyLog).setOnClickListener {
            emptyLogfile()
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
                    txtDebug.append(msg + "\n")
                    logToFile(msg)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }.start()
        }

        findViewById<Button>(R.id.btnDiagnostics).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        checkLocationPermission()

        swShowOverlay.isChecked = settings.getShowSpeedometer()
        swBroadcast.isChecked = settings.isBroadcastEnabled()

        swShowOverlay.setOnCheckedChangeListener { _, checked ->
            settings.setShowSpeedometer(checked)
        }

        swBroadcast.setOnCheckedChangeListener { _, checked ->
            settings.setBroadcastEnabled(checked)
        }

        btnStart.setOnClickListener {
            startService(Intent(this, DrivingService::class.java))
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, DrivingService::class.java)
            stopService(intent)
        }

        registerReceiver(speedReceiver, IntentFilter("xyz.hvdw.speedalert.SPEED_UPDATE"))
        registerReceiver(debugReceiver, IntentFilter("speedalert.debug"))
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
        unregisterReceiver(debugReceiver)
    }

    // ---------------------------------------------------------
    // RECEIVERS
    // ---------------------------------------------------------
    private val speedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val speed = intent?.getIntExtra("speed", -1) ?: -1
            val limit = intent?.getIntExtra("limit", -1) ?: -1
            val overspeed = intent?.getBooleanExtra("overspeed", false) ?: false
            val acc = intent?.getFloatExtra("accuracy", -1f) ?: -1f

            //val primaryColor = ContextCompat.getColor(this@MainActivity, R.color.primary)

            logToFile("Receiver: speed=$speed, limit=$limit, acc=$acc")

            txtSpeed.text = if (speed >= 0) {
                getString(R.string.speed_value, speed)
            } else {
                "--"
            }
            txtLimit.text = if (limit > 0) {
                getString(R.string.limit_value, limit)
            } else {
                "--"
            }
            txtStatus.text = if (acc >= 0) {
                "GPS accuracy: ${acc.toInt()} m"
            } else {
                "Waiting for GPS…"
            }

            if (overspeed) {
                txtSpeed.setTextColor(Color.RED)
                txtLimit.setTextColor(Color.RED)
            } else {
                txtSpeed.setTextColor(defaultTextColor)
                txtLimit.setTextColor(defaultTextColor)
            }
        }
    }

    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val msg = intent?.getStringExtra("msg") ?: return
            if (txtDebug.text.length > 50000) {   // 50k chars
                txtDebug.text = txtDebug.text.takeLast(20000)
            }

            txtDebug.append(msg + "\n")

            scrollDebug.post {
                scrollDebug.fullScroll(View.FOCUS_DOWN)
            }

            logToFile(msg)
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
    // LOGFILE COPY + SHARE
    // ---------------------------------------------------------
    private fun copyLogfile() {
        try {
            val src = File(filesDir, "speedalert.log")
            if (!src.exists()) {
                Toast.makeText(this, "Logfile does not exist yet", Toast.LENGTH_SHORT).show()
                return
            }

            val destDir = File("/sdcard/SpeedAlert")
            if (!destDir.exists()) destDir.mkdirs()

            val dest = File(destDir, "speedalert.log")

            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }

            Toast.makeText(this, "Copied to /sdcard/SpeedAlert/speedalert.log", Toast.LENGTH_LONG).show()
            logToFile("Logfile copied to shared storage")

        } catch (e: Exception) {
            Toast.makeText(this, "Copy failed: ${e.message}", Toast.LENGTH_LONG).show()
            logToFile("Copy failed: ${e.message}")
        }
    }

    private fun shareLogfile() {
        try {
            val file = File(filesDir, "speedalert.log")
            if (!file.exists()) {
                Toast.makeText(this, "Logfile does not exist yet", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Share logfile"))

        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
            logToFile("Share failed: ${e.message}")
        }
    }

    private fun emptyLogfile() {    
        try {
            val file = File(filesDir, "speedalert.log")
            if (file.exists()) {
                file.writeText("")   // clear file
                Toast.makeText(this, "Logfile emptied", Toast.LENGTH_SHORT).show()
                logToFile("Logfile emptied by user")
            } else {
                Toast.makeText(this, "Logfile does not exist yet", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to empty log: ${e.message}", Toast.LENGTH_LONG).show()
            logToFile("Failed to empty log: ${e.message}")
        }
    }

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


    /* private fun startGps() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500,
                0f
            ) { loc ->
                sendLocationToService(loc)
            }
        }
    } */

    /* private fun sendLocationToService(loc: Location) {
        val intent = Intent(this, DrivingService::class.java)
        intent.putExtra("location", loc)
        startService(intent)
    } */

}
