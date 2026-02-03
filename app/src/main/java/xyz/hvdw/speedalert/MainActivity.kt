package xyz.hvdw.speedalert

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import xyz.hvdw.speedalert.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsManager

    private val REQ_LOCATION = 1
    private val REQ_NOTIFICATION = 2

    // ---------------------------------------------------------
    // BROADCAST RECEIVERS
    // ---------------------------------------------------------
    private val speedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val speed = intent?.getIntExtra("speed", 0) ?: 0
            val limit = intent?.getIntExtra("limit", 0) ?: 0
            val ts = intent?.getLongExtra("timestamp", 0L) ?: 0L
            val acc = intent?.getFloatExtra("accuracy", -1f) ?: -1f
            val uptime = intent?.getLongExtra("uptime", 0L) ?: 0L

            // Speed + limit
            binding.textDebugSpeed.text = "Speed: $speed"
            binding.textDebugLimit.text = "Limit: $limit"

            // GPS timestamp
            if (ts > 0) {
                val secondsAgo = (System.currentTimeMillis() - ts) / 1000
                binding.textDebugGps.text = "GPS: fix $secondsAgo sec ago"
            } else {
                binding.textDebugGps.text = "GPS: no fix"
            }

            // Accuracy
            if (acc >= 0) {
                binding.textDebugAccuracy.text = "Acc: ${acc.toInt()} m"
            } else {
                binding.textDebugAccuracy.text = "Acc: unknown"
            }

            // Uptime
            binding.textDebugUptime.text = "Up: ${uptime}s"

            // Service is running
            binding.textDebugService.text = "Service: running"
        }
    }

    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: "unknown"
            val uptime = intent?.getLongExtra("uptime", 0L) ?: 0L

            when (status) {
                "running" -> binding.textDebugService.text = "Service: running"
                "gps_lost" -> binding.textDebugGps.text = "GPS: LOST"
                "no_gps_permission" -> binding.textDebugGps.text = "GPS: permission missing"
                "overlay_failed" -> binding.textDebugOverlay.text = "Overlay: FAILED"
                "stopped" -> binding.textDebugService.text = "Service: stopped"
                else -> binding.textDebugService.text = "Service: $status"
            }

            binding.textDebugUptime.text = "Up: ${uptime}s"
        }
    }

    // ---------------------------------------------------------
    // PERMISSION DEBUG PANEL
    // ---------------------------------------------------------
    private fun updatePermissionDebug() {
        val gps = if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) "GPS OK" else "GPS MISSING"

        val notif = if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) "Notifications OK" else "Notifications MISSING"

        val overlay = if (Settings.canDrawOverlays(this)) "Overlay OK" else "Overlay MISSING"

        binding.textDebugPermissions.text = "Permissions: $gps | $notif | $overlay"
    }

    // ---------------------------------------------------------
    // OVERLAY PERMISSION
    // ---------------------------------------------------------
    /* private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }

            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open overlay settings", Toast.LENGTH_LONG).show()
            }
        }
    } */

    /* Vasily91 Display-Media-titles */
    private fun requestOverlayPermission() {
        // Draw over other apps permission
        if (!Settings.canDrawOverlays(this)) {
            val intentOverlays = Intent()
            intentOverlays.setAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intentOverlays.setData("package:$packageName".toUri())
            intentOverlays.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intentOverlays.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            intentOverlays.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            startActivity(intentOverlays)
        }
    }


    // ---------------------------------------------------------
    // SOUND PICKER
    // ---------------------------------------------------------
    private val pickSound =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) settings.setCustomSound(uri)
        }

    // ---------------------------------------------------------
    // ACTIVITY LIFECYCLE
    // ---------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsManager(this)

        updatePermissionDebug()
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(speedReceiver, IntentFilter("SPEED_UPDATE"))
        registerReceiver(serviceStatusReceiver, IntentFilter("SERVICE_STATUS"))
        updatePermissionDebug()
        binding.textDebugGps.text = "GPS: waiting..."
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(speedReceiver)
        unregisterReceiver(serviceStatusReceiver)
    }

    // ---------------------------------------------------------
    // UI SETUP
    // ---------------------------------------------------------
    private fun setupUI() {

        // Expandable headers
        binding.headerSettings.icon = ContextCompat.getDrawable(this, R.drawable.ic_expand_more)
        binding.headerTools.icon = ContextCompat.getDrawable(this, R.drawable.ic_expand_more)
        binding.debugheader.icon = ContextCompat.getDrawable(this, R.drawable.ic_expand_more)
        binding.headerDisclaimer.icon = ContextCompat.getDrawable(this, R.drawable.ic_expand_more)

        binding.headerSettings.setOnClickListener {
            toggleSection(binding.headerSettings, binding.layoutSettingsContent)
        }
        binding.headerTools.setOnClickListener {
            toggleSection(binding.headerTools, binding.layoutToolsContent)
        }
        binding.debugheader.setOnClickListener {
            toggleSection(binding.debugheader, binding.debugContent)
        }
        binding.headerDisclaimer.setOnClickListener {
            toggleSection(binding.headerDisclaimer, binding.layoutDisclaimerContent)
        }

        // ---------------------------------------------------------
        // START SERVICE BUTTON
        // ---------------------------------------------------------
        binding.btnStart.setOnClickListener {
            binding.textDebugService.text = "Service: starting..."

            // Notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIFICATION
                )
                return@setOnClickListener
            }

            // Location permission
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQ_LOCATION
                )
                return@setOnClickListener
            }

            // Overlay permission
            if (settings.getShowSpeedometer() && !Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
                return@setOnClickListener
            }

            updatePermissionDebug()

            // Start service
            val intent = Intent(this, DrivingService::class.java)
            ContextCompat.startForegroundService(this, intent)

            binding.textDebugService.text = "Service: running"
        }

        // ---------------------------------------------------------
        // STOP SERVICE BUTTON
        // ---------------------------------------------------------
        binding.btnStop.setOnClickListener {
            stopService(Intent(this, DrivingService::class.java))
            binding.textDebugService.text = "Service: stopped"
        }

        // ---------------------------------------------------------
        // TEST BUTTONS
        // ---------------------------------------------------------
        binding.btnTestSound.setOnClickListener {
            val custom = settings.getCustomSound()
            val mp = if (custom != null) MediaPlayer.create(this, custom)
            else MediaPlayer.create(this, R.raw.beep)
            mp?.setOnCompletionListener { it.release() }
            mp?.start()
        }

        binding.btnTestToast.setOnClickListener {
            Toast.makeText(this, "Test toast", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestNotification.setOnClickListener {
            val sn = ServiceNotification(this)
            sn.createChannel()

            val notification = NotificationCompat.Builder(
                this,
                ServiceNotification.CHANNEL_ID
            )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Test notification")
                .setContentText("This is a test")
                .build()

            NotificationManagerCompat.from(this).notify(999, notification)
        }

        // ---------------------------------------------------------
        // SETTINGS SWITCHES
        // ---------------------------------------------------------
        binding.switchSpeedo.isChecked = settings.getShowSpeedometer()
        binding.switchTransparency.isChecked = settings.getSemiTransparent()
        binding.switchMph.isChecked = settings.getUseMph()
        binding.switchBroadcast.isChecked = settings.isBroadcastEnabled()

        binding.switchSpeedo.setOnCheckedChangeListener { _, checked ->
            settings.setShowSpeedometer(checked)
            sendOverlayUpdate()
        }

        binding.switchTransparency.setOnCheckedChangeListener { _, checked ->
            settings.setSemiTransparent(checked)
            sendOverlayUpdate()
        }

        binding.switchBroadcast.setOnCheckedChangeListener { _, checked ->
            settings.setBroadcastEnabled(checked)
            sendOverlayUpdate()
        }

        binding.switchMph.setOnCheckedChangeListener { _, checked ->
            settings.setUseMph(checked)
            sendOverlayUpdate()
        }

        // ---------------------------------------------------------
        // OVERSPEED SLIDER
        // ---------------------------------------------------------
        val saved = settings.getOverspeedPercentage()
        binding.sliderOverspeed.value = saved.toFloat()
        binding.textOverspeedValue.text = "$saved%"

        binding.sliderOverspeed.addOnChangeListener { _, value, fromUser ->
            val v = value.toInt()
            binding.textOverspeedValue.text = "$v%"
            if (fromUser) settings.setOverspeedPercentage(v)
        }
    }

    // ---------------------------------------------------------
    // PERMISSION CALLBACK
    // ---------------------------------------------------------
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            binding.textDebugPermissions.text = "Permissions: DENIED"
            return
        }

        binding.textDebugPermissions.text = "Permissions: GRANTED"
        updatePermissionDebug()
    }

    // ---------------------------------------------------------
    // OVERLAY UPDATE
    // ---------------------------------------------------------
    private fun isServiceRunning(): Boolean = DrivingService.isRunning

    private fun sendOverlayUpdate() {
        if (!isServiceRunning()) return

        val intent = Intent(this, DrivingService::class.java).apply {
            action = DrivingService.ACTION_UPDATE_OVERLAY
        }
        startForegroundService(intent)
    }

    // ---------------------------------------------------------
    // EXPAND / COLLAPSE
    // ---------------------------------------------------------
    private fun toggleSection(header: View, content: View) {
        val isVisible = content.visibility == View.VISIBLE

        content.visibility = if (isVisible) View.GONE else View.VISIBLE

        val icon = if (isVisible) R.drawable.ic_expand_more else R.drawable.ic_expand_less
        (header as? ViewGroup)?.let {
            (header as com.google.android.material.button.MaterialButton).icon =
                ContextCompat.getDrawable(this, icon)
        }
    }
}
