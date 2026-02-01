package xyz.hvdw.speedalert

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*

class DrivingService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var settings: SettingsManager
    private val repo = SpeedLimitRepository()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaPlayer: MediaPlayer? = null
    private var speedometer: FloatingSpeedometer? = null
    private var mediaBroadcast: MediaBroadcastManager? = null

    private var lastLimit: Int? = null
    private var lastLimitFetchTime = 0L
    private val kalman = KalmanFilter()

    private var lastGpsFixTime = 0L

    companion object {
        const val ACTION_UPDATE_OVERLAY = "xyz.hvdw.speedalert.UPDATE_OVERLAY"
        private const val NOTIF_ID = 1
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        settings = SettingsManager(this)
        fused = LocationServices.getFusedLocationProviderClient(this)

        // Foreground notification
        val notif = ServiceNotification(this)
        notif.createChannel()
        startForeground(NOTIF_ID, notif.createNotification())

        sendStatus("running")

        // Overlay (safe)
        if (settings.getShowSpeedometer() && Settings.canDrawOverlays(this)) {
            speedometer = FloatingSpeedometer(this, settings)
            speedometer?.show()
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
    // LOCATION UPDATES
    // ---------------------------------------------------------
    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        )
            .setMinUpdateDistanceMeters(2f)
            .build()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            sendStatus("no_gps_permission")
            stopSelf()
            return
        }

        fused.requestLocationUpdates(req, locationCallback, mainLooper)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(res: LocationResult) {
            val loc = res.lastLocation
            if (loc == null) {
                sendGpsLost()
                return
            }

            lastGpsFixTime = System.currentTimeMillis()
            scope.launch { handleLocation(loc) }
        }
    }

    // ---------------------------------------------------------
    // GPS WATCHDOG (detects GPS loss)
    // ---------------------------------------------------------
    private fun startGpsWatchdog() {
        scope.launch {
            while (isActive) {
                delay(2000)

                val now = System.currentTimeMillis()
                if (lastGpsFixTime != 0L && now - lastGpsFixTime > 10_000) {
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

        // Fetch speed limit every 10 seconds
        val now = System.currentTimeMillis()
        val limit = if (now - lastLimitFetchTime > 10_000) {
            lastLimitFetchTime = now
            repo.getSpeedLimit(lat, lon)?.also { lastLimit = it }
        } else lastLimit

        // Update overlay
        speedometer?.updateSpeed(intSpeed, limit, false)

        // Update UI
        val updateIntent = Intent("SPEED_UPDATE")
        updateIntent.putExtra("speed", intSpeed)
        updateIntent.putExtra("limit", limit ?: -1)
        updateIntent.putExtra("timestamp", now)
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

            if (isOverspeed) playOverspeedWarning(limit)
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
        sendBroadcast(intent)
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        fused.removeLocationUpdates(locationCallback)
        mediaPlayer?.release()
        speedometer?.hide()
        scope.cancel()
        mediaBroadcast?.stop()

        sendStatus("stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
