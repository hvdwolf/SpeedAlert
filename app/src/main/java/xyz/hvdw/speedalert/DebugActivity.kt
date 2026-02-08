package xyz.hvdw.speedalert

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class DebugActivity : AppCompatActivity() {

    private lateinit var txtDebugFull: TextView
    private lateinit var scrollDebugFull: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        txtDebugFull = findViewById(R.id.txtDebugFull)
        scrollDebugFull = findViewById(R.id.scrollDebugFull)

        // Load logfile
        val file = File(filesDir, "speedalert.log")
        if (file.exists()) {
            txtDebugFull.text = file.readText()
        } else {
            txtDebugFull.text = "Logfile does not exist yet."
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
        }
    }
}
