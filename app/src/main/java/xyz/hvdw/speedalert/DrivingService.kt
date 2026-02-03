package xyz.hvdw.speedalert

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class DrivingService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var settings: SettingsManager
    private val repo = MultiProviderSpeedLimitRepository()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaPlayer: MediaPlayer? = null
    private var speedometer: FloatingSpeedometer? = null
    private var mediaBroadcast: MediaBroadcastManager? = null

    private var lastLimit: Int? = null
    private var lastLimitFetchTime = 0L
    private val kalman = KalmanFilter()

    private var lastGpsFixTime = 0L
    private var fusedWorking = false

    private var serviceStartTime = 0L
    private lateinit var logFile: File

    companion object {
        const val ACTION_UPDATE_OVERLAY = "xyz.hvdw.speedalert.UPDATE_OVERLAY"
        private const val NOTIF_ID = 1
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        serviceStartTime = System.currentTimeMillis()

        settings = SettingsManager(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        logFile = File(filesDir, "driving_service.log")
        logLine("Service created")

        // Foreground notification
        val notif = ServiceNotification(this)
        notif.createChannel()
        startForeground(NOTIF_ID, notif.createNotification())

        sendStatus("running")

        // Overlay
        if (settings.getShowSpeedometer() && Settings.canDrawOverlays(this)) {
            try {
                speedometer = FloatingSpeedometer(this, settings)
                speedometer?.show()
            } catch (e: Exception) {
                e.printStackTrace()
                logLine("Overlay failed: ${e.message}")
                sendStatus("overlay_failed")
            }
        }

        // Media broadcast
        if (settings.isBroadcastEnabled()) {
            mediaBroadcast = MediaBroadcastManager(this)
            mediaBroadcast?.start()
        }

        initMediaPlayer()
        startLocationUpdates()
        startGpsWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE_OVERLAY) {
            speedometer?.updateStyle(settings)
        }
        return START_STICKY
    }

    // ---------------------------------------------------------
    // MEDIA PLAYER
    // ---------------------------------------------------------
    private fun initMediaPlayer() {
        val custom = settings.getCustomSound()
        mediaPlayer = if (custom != null) {
            MediaPlayer.create(this, custom)
        } else {
            MediaPlayer.create(this, R.raw.beep)
        }
    }

    // ---------------------------------------------------------
    // LOCATION UPDATES (Android 10+)
    // ---------------------------------------------------------
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            sendStatus("no_gps_permission")
            logLine("No GPS permission, stopping service")
            stopSelf()
            return
        }

        // Fused provider
        try {
            val req = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            )
                .setMinUpdateDistanceMeters(2f)
                .build()

            fused.requestLocationUpdates(req, locationCallback, mainLooper)
            fusedWorking = true
            logLine("Fused location updates requested")
        } catch (e: Exception) {
            fusedWorking = false
            logLine("Fused location failed: ${e.message}")
        }

        // Fallback LocationManager
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                2f,
                gpsListener
            )
            logLine("LocationManager GPS_PROVIDER updates requested")
        } catch (e: Exception) {
            logLine("LocationManager request failed: ${e.message}")
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(res: LocationResult) {
            val loc = res.lastLocation ?: return
            fusedWorking = true
            lastGpsFixTime = System.currentTimeMillis()
            logLine("Fused location: ${loc.latitude}, ${loc.longitude}, acc=${loc.accuracy}")
            scope.launch { handleLocation(loc) }
        }
    }

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            if (!fusedWorking) {
                lastGpsFixTime = System.currentTimeMillis()
                logLine("Fallback location: ${loc.latitude}, ${loc.longitude}, acc=${loc.accuracy}")
                scope.launch { handleLocation(loc) }
            }
        }
    }

    // ---------------------------------------------------------
    // GPS WATCHDOG
    // ---------------------------------------------------------
    private fun startGpsWatchdog() {
        scope.launch {
            while (isActive) {
                delay(2000)

                val now = System.currentTimeMillis()
                if (lastGpsFixTime != 0L && now - lastGpsFixTime > 10_000) {
                    logLine("GPS lost (no fix for >10s)")
                    sendGpsLost()
                }
            }
        }
    }

    private fun sendGpsLost() {
        val intent = Intent("SPEED_UPDATE")
        intent.putExtra("speed", -1)
        intent.putExtra("limit", -1)
        intent.putExtra("timestamp", System.currentTimeMillis())
        intent.putExtra("accuracy", -1f)
        intent.putExtra("uptime", getServiceUptimeSeconds())
        sendBroadcast(intent)

        sendStatus("gps_lost")
    }

    // ---------------------------------------------------------
    // LOCATION HANDLING
    // ---------------------------------------------------------
    private suspend fun handleLocation(loc: Location) {
        val rawSpeed = loc.speed * 3.6
        val filteredSpeed = kalman.update(rawSpeed)
        val intSpeed = filteredSpeed.toInt()

        val lat = loc.latitude
        val lon = loc.longitude
        val accuracy = loc.accuracy
        val now = System.currentTimeMillis()

        // Fetch speed limit every 10 seconds
        val limit = if (now - lastLimitFetchTime > 10_000) {
            lastLimitFetchTime = now
            val fetched = repo.getSpeedLimit(lat, lon)
            lastLimit = fetched
            logLine("Speed limit fetched: $fetched at $lat,$lon")
            fetched
        } else lastLimit

        // Update overlay
        speedometer?.updateSpeed(intSpeed, limit, false)

        // Update UI
        val updateIntent = Intent("SPEED_UPDATE")
        updateIntent.putExtra("speed", intSpeed)
        updateIntent.putExtra("limit", limit ?: -1)
        updateIntent.putExtra("timestamp", now)
        updateIntent.putExtra("accuracy", accuracy)
        updateIntent.putExtra("uptime", getServiceUptimeSeconds())
        sendBroadcast(updateIntent)

        // Media broadcast
        if (settings.isBroadcastEnabled() && limit != null) {
            mediaBroadcast?.updateMetadata(
                appName = getString(R.string.app_name),
                speed = intSpeed,
                limit = limit,
                useMph = settings.getUseMph()
            )
        }

        // Overspeed logic
        if (limit != null) {
            val overshootPercent = settings.getOverspeedPercentage()
            val threshold = limit * (1 + overshootPercent / 100.0)
            val isOverspeed = filteredSpeed > threshold

            speedometer?.updateSpeed(intSpeed, limit, isOverspeed)

            if (isOverspeed) {
                logLine("Overspeed: speed=$intSpeed, limit=$limit")
                playOverspeedWarning(limit)
            }
        }
    }

    // ---------------------------------------------------------
    // OVERSPEED WARNING
    // ---------------------------------------------------------
    private suspend fun playOverspeedWarning(limit: Int) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                this@DrivingService,
                getString(R.string.overspeed_warning, limit),
                Toast.LENGTH_SHORT
            ).show()
        }

        mediaPlayer?.let {
            if (!it.isPlaying) it.start()
        }
    }

    // ---------------------------------------------------------
    // STATUS BROADCAST
    // ---------------------------------------------------------
    private fun sendStatus(status: String) {
        val intent = Intent("SERVICE_STATUS")
        intent.putExtra("status", status)
        intent.putExtra("uptime", getServiceUptimeSeconds())
        sendBroadcast(intent)
        logLine("Status: $status, uptime=${getServiceUptimeSeconds()}s")
    }

    // ---------------------------------------------------------
    // UPTIME
    // ---------------------------------------------------------
    private fun getServiceUptimeSeconds(): Long {
        return (System.currentTimeMillis() - serviceStartTime) / 1000
    }

    // ---------------------------------------------------------
    // LOGGING
    // ---------------------------------------------------------
    private fun logLine(msg: String) {
        try {
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(System.currentTimeMillis())
            logFile.appendText("[$ts] $msg\n")
        } catch (_: Exception) {}
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        try { fused.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        try { locationManager.removeUpdates(gpsListener) } catch (_: Exception) {}

        mediaPlayer?.release()
        speedometer?.hide()
        scope.cancel()
        mediaBroadcast?.stop()

        sendStatus("stopped")
        logLine("Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
