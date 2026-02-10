package xyz.hvdw.speedalert

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        edtLat = findViewById(R.id.edtLat)
        edtLon = findViewById(R.id.edtLon)

        txtDebugFull = findViewById(R.id.txtDebugFull)
        scrollDebugFull = findViewById(R.id.scrollDebugFull)

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
                    val result = repo.getSpeedLimit(lat, lon)
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

    private fun testSpeedLimitLookup(lat: Double?, lon: Double?) {
        // your existing logic
    }

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


}
