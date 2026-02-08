package xyz.hvdw.speedalert

import android.content.Context
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var txtDiag: TextView
    private lateinit var scroll: ScrollView

    private lateinit var repo: SpeedLimitRepository
    private var lastLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.diagnostics)

        repo = SpeedLimitRepository(this)

        txtDiag = findViewById(R.id.txtDiag)
        scroll = findViewById(R.id.scrollDiag)

        findViewById<Button>(R.id.btnDiagRawOverpass).setOnClickListener {
            runRawOverpassTest()
        }

        findViewById<Button>(R.id.btnDiagWebOverpass).setOnClickListener {
            runWebOverpassTest()
        }

        findViewById<Button>(R.id.btnDiagRawNominatim).setOnClickListener {
            runRawNominatimTest()
        }

        findViewById<Button>(R.id.btnDiagWebNominatim).setOnClickListener {
            runWebNominatimTest()
        }

        findViewById<Button>(R.id.btnDiagShowRawOverpass).setOnClickListener {
            showRawOverpassResponse()
        }

        //findViewById<Button>(R.id.btnDiagNetwork).setOnClickListener {
        //    showNetworkType()
        //}

        findViewById<Button>(R.id.btnDiagLocation).setOnClickListener {
            showLastLocation()
        }
    }

    private fun append(msg: String) {
        txtDiag.append(msg + "\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ---------------------------------------------------------
    // TESTS
    // ---------------------------------------------------------

    private fun runRawOverpassTest() {
        append("Testing RAW Overpass…")
        Thread {
            val lat = 52.48
            val lon = 6.13
            val result = repo.testRawOverpass(lat, lon)
            runOnUiThread { append("RAW Overpass result: $result") }
        }.start()
    }

    private fun runWebOverpassTest() {
        append("Testing WebView Overpass…")
        Thread {
            val lat = 52.48
            val lon = 6.13
            val result = repo.testWebOverpass(lat, lon)
            runOnUiThread { append("WebView Overpass result: $result") }
        }.start()
    }

    private fun runRawNominatimTest() {
        append("Testing RAW Nominatim…")
        Thread {
            val lat = 52.48
            val lon = 6.13
            val result = repo.testRawNominatim(lat, lon)
            runOnUiThread { append("RAW Nominatim result: $result") }
        }.start()
    }

    private fun runWebNominatimTest() {
        append("Testing WebView Nominatim…")
        Thread {
            val lat = 52.48
            val lon = 6.13
            val result = repo.testWebNominatim(lat, lon)
            runOnUiThread { append("WebView Nominatim result: $result") }
        }.start()
    }

    private fun showRawOverpassResponse() {
        append("Fetching raw Overpass response…")
        Thread {
            val lat = 52.48
            val lon = 6.13
            val raw = repo.fetchRawOverpassResponse(lat, lon)
            runOnUiThread {
                append("Raw Overpass response:\n$raw")
            }
        }.start()
    }

    private fun showNetworkType() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return append("No network")
        val caps = cm.getNetworkCapabilities(net) ?: return append("Unknown network")

        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Other"
        }

        append("Network type: $type")
    }

    private fun showLastLocation() {
        if (lastLocation == null) {
            append("No location available")
        } else {
            append("Last location: ${lastLocation!!.latitude}, ${lastLocation!!.longitude}")
        }
    }
}
