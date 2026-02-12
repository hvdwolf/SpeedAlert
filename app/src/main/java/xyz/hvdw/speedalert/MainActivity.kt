package xyz.hvdw.speedalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var txtSpeed: TextView
    private lateinit var txtLimit: TextView
    private lateinit var txtStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var swShowOverlay: Switch
    private lateinit var swBroadcast: Switch
    private lateinit var swOverspeedMode: Switch
    private lateinit var seekOverspeed: SeekBar
    private lateinit var seekBrightness: SeekBar
    private lateinit var txtOverspeedLabel: TextView

    // NEW: Speedometer size controls
    private lateinit var seekSpeedoSize: SeekBar
    private lateinit var txtSpeedoSizeValue: TextView

    private val LOCATION_REQUEST = 1001
    private var defaultTextColor: Int = 0

    private lateinit var settings: SettingsManager
    private lateinit var floatingSpeedometer: FloatingSpeedometer

    private val prefs by lazy {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private val sizeLabels by lazy {
        arrayOf(
            getString(R.string.speedo_size_smallest),
            getString(R.string.speedo_size_smaller),
            getString(R.string.speedo_size_default),
            getString(R.string.speedo_size_bigger),
            getString(R.string.speedo_size_biggest)
        )
    }

    private val sizeScales = floatArrayOf(
        0.60f,
        0.80f,
        1.00f,
        1.20f,
        1.40f
    )

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
        swOverspeedMode = findViewById(R.id.swOverspeedMode)
        seekOverspeed = findViewById(R.id.seekOverspeed)
        seekBrightness = findViewById(R.id.seekBrightness)
        txtOverspeedLabel = findViewById(R.id.txtOverspeedLabel)

        // NEW: Speedometer size
        seekSpeedoSize = findViewById(R.id.seekSpeedoSize)
        txtSpeedoSizeValue = findViewById(R.id.txtSpeedoSizeValue)

        defaultTextColor = txtSpeed.currentTextColor

        floatingSpeedometer = FloatingSpeedometer(this, settings)

        // ---------------------------------------------------------
        // SPEEDOMETER SIZE INITIALIZATION
        // ---------------------------------------------------------
        val savedScale = prefs.getFloat("overlay_text_scale", 1.0f)
        val index = sizeScales.indexOfFirst { it == savedScale }.let { if (it == -1) 2 else it }


        seekSpeedoSize.progress = index
        txtSpeedoSizeValue.text = sizeLabels[index]

        seekSpeedoSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                txtSpeedoSizeValue.text = sizeLabels[value]
                prefs.edit().putFloat("overlay_text_scale", sizeScales[value]).apply()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // ---------------------------------------------------------
        // OVERSPEED MODE INITIALIZATION
        // ---------------------------------------------------------
        val isPctMode = settings.isOverspeedModePercentage()
        swOverspeedMode.isChecked = isPctMode
        updateOverspeedUI(isPctMode)

        swOverspeedMode.setOnCheckedChangeListener { _, checked ->
            settings.setOverspeedModePercentage(checked)
            updateOverspeedUI(checked)
        }

        // ---------------------------------------------------------
        // OVERSPEED SLIDER LISTENER
        // ---------------------------------------------------------
        seekOverspeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                updateOverspeedLabel(value)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}

            override fun onStopTrackingTouch(sb: SeekBar?) {
                val value = seekOverspeed.progress

                if (settings.isOverspeedModePercentage()) {
                    settings.setOverspeedPercentage(value)
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.overspeed_saved_percent, value),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    settings.setOverspeedFixedKmh(value)
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.overspeed_saved_fixed, value),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        // ---------------------------------------------------------
        // BRIGHTNESS SLIDER LISTENER
        // ---------------------------------------------------------
        seekBrightness.progress = prefs.getInt("speedo_brightness", 100)

        seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                prefs.edit().putInt("speedo_brightness", value).apply()
                updateFloatingSpeedometerBrightness()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // ---------------------------------------------------------
        // BUTTONS
        // ---------------------------------------------------------
        findViewById<Button>(R.id.btnDebugScreen).setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }

        // ---------------------------------------------------------
        // BEEP VOLUME SLIDER AND TEST BUTTON
        // ---------------------------------------------------------
        val seekBeepVolume = findViewById<SeekBar>(R.id.seekBeepVolume)
        val txtBeepVolumeLabel = findViewById<TextView>(R.id.txtBeepVolumeLabel)
        val btnTestBeep = findViewById<Button>(R.id.btnTestBeep)

        val savedVol = settings.getBeepVolume()
        seekBeepVolume.progress = (savedVol * 100).toInt()
        txtBeepVolumeLabel.text = getString(
            R.string.beep_volume_label,
            (savedVol * 100).toInt()
        )

        seekBeepVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val vol = progress / 100f
                settings.setBeepVolume(vol)
                txtBeepVolumeLabel.text = getString(R.string.beep_volume_label, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnTestBeep.setOnClickListener {
            playTestBeep()
        }

        // ---------------------------------------------------------
        // SWITCHES
        // ---------------------------------------------------------
        swShowOverlay.isChecked = settings.getShowSpeedometer()
        swBroadcast.isChecked = settings.isBroadcastEnabled()

        swShowOverlay.setOnCheckedChangeListener { _, checked ->
            settings.setShowSpeedometer(checked)
        }

        swBroadcast.setOnCheckedChangeListener { _, checked ->
            settings.setBroadcastEnabled(checked)
        }

        // ---------------------------------------------------------
        // SERVICE START/STOP
        // ---------------------------------------------------------
        btnStart.setOnClickListener {
            startService(Intent(this, DrivingService::class.java))
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, DrivingService::class.java))
        }

        // ---------------------------------------------------------
        // PERMISSIONS + RECEIVERS
        // ---------------------------------------------------------
        checkLocationPermission()
        registerReceiver(speedReceiver, IntentFilter("xyz.hvdw.speedalert.SPEED_UPDATE"))
    }

    override fun onResume() {
        super.onResume()

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
    // OVERSPEED UI UPDATE LOGIC
    // ---------------------------------------------------------
    private fun updateOverspeedUI(isPctMode: Boolean) {
        if (isPctMode) {
            seekOverspeed.max = 30
            seekOverspeed.progress = settings.getOverspeedPercentage()
            updateOverspeedLabel(settings.getOverspeedPercentage())
            swOverspeedMode.text = getString(R.string.overspeed_mode_percentage)
        } else {
            seekOverspeed.max = 20
            seekOverspeed.progress = settings.getOverspeedFixedKmh()
            updateOverspeedLabel(settings.getOverspeedFixedKmh())
            swOverspeedMode.text = getString(R.string.overspeed_mode_fixed)
        }
    }

    private fun updateOverspeedLabel(value: Int) {
        if (settings.isOverspeedModePercentage()) {
            txtOverspeedLabel.text = getString(R.string.overspeed_label_percent, value)
        } else {
            txtOverspeedLabel.text = getString(R.string.overspeed_label_fixed, value)
        }
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

            if (speed >= 0) {
                val unit = if (settings.usesMph()) "mph" else "km/h"
                txtSpeed.text = "$speed $unit"
            } else {
                txtSpeed.text = "--"
            }

            if (limit > 0) {
                val unit = if (settings.usesMph()) "mph" else "km/h"
                txtLimit.text = "$limit $unit"
            } else {
                txtLimit.text = "--"
            }

            if (acc >= 0) {
                txtStatus.text = getString(R.string.gps_accuracy, acc.toInt())
            } else {
                txtStatus.text = getString(R.string.gps_waiting)
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
    }

    private fun playTestBeep() {
        val vol = settings.getBeepVolume()

        val mp = MediaPlayer.create(this, R.raw.beep)
        mp.setVolume(vol, vol)
        mp.setOnCompletionListener { it.release() }
        mp.start()
    }

    private fun updateFloatingSpeedometerBrightness() {
        floatingSpeedometer.updateBrightness()
    }
}
