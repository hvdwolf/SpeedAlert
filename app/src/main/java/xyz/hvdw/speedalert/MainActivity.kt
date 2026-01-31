package xyz.hvdw.speedalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import xyz.hvdw.speedalert.databinding.ActivityMainBinding

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsManager

    private val REQ_LOCATION = 1
    private val REQ_NOTIFICATION = 2

    // ---------------------------------------------------------
    // CHINESE HEAD UNIT DETECTOR
    // ---------------------------------------------------------
    private fun isChineseHeadUnit(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val model = Build.MODEL.lowercase()

        return manufacturer.contains("mtcd") ||
               manufacturer.contains("mtce") ||
               brand.contains("fyt") ||
               brand.contains("joying") ||
               brand.contains("dasaita") ||
               brand.contains("hct") ||
               model.contains("px5") ||
               model.contains("px6") ||
               model.contains("px30") ||
               model.contains("ts10") ||
               model.contains("ts18")
    }

    // ---------------------------------------------------------
    // OVERLAY PERMISSION HANDLING
    // ---------------------------------------------------------
    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasOverlayPermission()) {
                Toast.makeText(this, getString(R.string.overlay_permission_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.overlay_permission_missing), Toast.LENGTH_LONG).show()
            }
        }

    private fun hasOverlayPermission(): Boolean {
        // Chinese head units often lie about overlay permission
        if (isChineseHeadUnit()) return true

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (isChineseHeadUnit()) {
            Toast.makeText(this, "Overlay permission assumed granted on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
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

        setupUI()
        requestLocationPermission()
        requestNotificationPermissionIfNeeded()
    }

    // ---------------------------------------------------------
    // UI SETUP
    // ---------------------------------------------------------
    private fun setupUI() {

        // Disclaimer toggle
        binding.txtDisclaimerHeader.setOnClickListener {
            val body = binding.txtDisclaimerBody
            if (body.visibility == View.VISIBLE) {
                body.visibility = View.GONE
                binding.txtDisclaimerHeader.text = getString(R.string.disclaimer_expand)
            } else {
                body.visibility = View.VISIBLE
                binding.txtDisclaimerHeader.text = getString(R.string.disclaimer_collapse)
            }
        }

        // Start service
        binding.btnStart.setOnClickListener {

            if (!hasOverlayPermission()) {
                requestOverlayPermission()
                return@setOnClickListener
            }

            if (hasAllPermissions()) {
                startForegroundService(Intent(this, DrivingService::class.java))
            } else {
                requestLocationPermission()
                requestNotificationPermissionIfNeeded()
            }
        }

        // Stop service
        binding.btnStop.setOnClickListener {
            stopService(Intent(this, DrivingService::class.java))
        }

        // Pick custom sound
        binding.btnSelectSound.setOnClickListener {
            pickSound.launch(getString(R.string.mime_audio))
        }

        // Reset custom sound
        binding.btnResetSound.setOnClickListener {
            settings.resetCustomSound()
            Toast.makeText(this, getString(R.string.sound_reset_default), Toast.LENGTH_SHORT).show()
            sendOverlayUpdate()
        }

        // ----------------- OVERSPEED SLIDER -----------------
        val saved = settings.getOverspeedPercentage()
        binding.sliderOverspeed.value = saved.toFloat()
        binding.textOverspeedValue.text = "$saved%"

        binding.sliderOverspeed.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}
                override fun onStopTrackingTouch(slider: Slider) {
                    settings.setOverspeedPercentage(slider.value.toInt())
                }
            }
        )

        binding.sliderOverspeed.addOnChangeListener { _, value, fromUser ->
            val v = value.toInt()
            binding.textOverspeedValue.text = "$v%"
            if (fromUser) settings.setOverspeedPercentage(v)
        }
        // ---------------------------------------------------

        // Switches
        binding.switchSpeedo.isChecked = settings.getShowSpeedometer()
        binding.switchTransparency.isChecked = settings.getSemiTransparent()
        binding.switchMph.isChecked = settings.getUseMph()

        binding.switchSpeedo.setOnCheckedChangeListener { _, checked ->
            settings.setShowSpeedometer(checked)
            sendOverlayUpdate()
        }

        binding.switchTransparency.setOnCheckedChangeListener { _, checked ->
            settings.setSemiTransparent(checked)
            sendOverlayUpdate()
        }

        binding.switchMph.setOnCheckedChangeListener { _, checked ->
            settings.setUseMph(checked)
            sendOverlayUpdate()
        }

        // Test sound
        binding.btnTestSound.setOnClickListener {
            val custom = settings.getCustomSound()
            val mp = if (custom != null) MediaPlayer.create(this, custom)
                     else MediaPlayer.create(this, R.raw.beep)
            mp?.setOnCompletionListener { player -> player.release() }
            mp?.start()
        }

        // Test toast
        binding.btnTestToast.setOnClickListener {
            Toast.makeText(this, getString(R.string.test_toast), Toast.LENGTH_SHORT).show()
        }

        // Test notification
        binding.btnTestNotification.setOnClickListener {
            val sn = ServiceNotification(this)
            sn.createChannel()

            val notification = androidx.core.app.NotificationCompat.Builder(
                this,
                ServiceNotification.CHANNEL_ID
            )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(R.string.test_notification_title))
                .setContentText(getString(R.string.test_notification_text))
                .build()

            androidx.core.app.NotificationManagerCompat.from(this)
                .notify(999, notification)
        }
    }

    // ---------------------------------------------------------
    // PERMISSIONS
    // ---------------------------------------------------------
    private fun hasAllPermissions(): Boolean {
        val locationGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val notificationGranted =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        return locationGranted && notificationGranted
    }

    private fun requestLocationPermission() {
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
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
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
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_NOTIFICATION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (hasAllPermissions() && hasOverlayPermission()) {
                startForegroundService(Intent(this, DrivingService::class.java))
            }
        }
    }

    // ---------------------------------------------------------
    // OVERLAY UPDATE
    // ---------------------------------------------------------
    private fun isServiceRunning(): Boolean {
        return DrivingService.isRunning
    }

    private fun sendOverlayUpdate() {
        if (!isServiceRunning()) return

        val intent = Intent(this, DrivingService::class.java).apply {
            action = DrivingService.ACTION_UPDATE_OVERLAY
        }
        startForegroundService(intent)
    }
}
