package xyz.hvdw.speedalert

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
import android.provider.Settings
import android.os.Build
import android.os.IBinder
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

        sendBroadcast(Intent("SERVICE_STATUS").apply {
            putExtra("status", "running")
        })


        // Overlay
        if (settings.getShowSpeedometer()) {
            if (Settings.canDrawOverlays(this)) {
                speedometer = FloatingSpeedometer(this, settings)
                speedometer?.show()
            } else {
                // Optional: log or toast once, but don’t crash the service
            }
        }


        if (settings.isBroadcastEnabled()) {
            mediaBroadcast = MediaBroadcastManager(this)
            mediaBroadcast?.start()
        }

        initMediaPlayer()
        startLocationUpdates()
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
            stopSelf()
            return
        }

        fused.requestLocationUpdates(req, locationCallback, mainLooper)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(res: LocationResult) {
            val loc = res.lastLocation ?: return
            scope.launch { handleLocation(loc) }
        }
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

        // Fetch speed limit only every 10 seconds to avoid API spam
        val now = System.currentTimeMillis()
        val limit = if (now - lastLimitFetchTime > 10_000) {
            lastLimitFetchTime = now
            repo.getSpeedLimit(lat, lon)?.also { lastLimit = it }
        } else {
            lastLimit
        }

        if (limit == null) {
            speedometer?.updateSpeed(intSpeed, null, false)
            return
        }

        if (settings.isBroadcastEnabled()) {
            mediaBroadcast?.updateMetadata(
                appName = getString(R.string.app_name),
                speed = intSpeed,
                limit = limit,
                useMph = settings.getUseMph()
            )
        }


        val overshootPercent = settings.getOverspeedPercentage()
        val threshold = limit * (1 + overshootPercent / 100.0)
        val isOverspeed = filteredSpeed > threshold

        speedometer?.updateSpeed(intSpeed, limit, isOverspeed)

        // Update main screen values
        val updateIntent = Intent("SPEED_UPDATE")
        updateIntent.putExtra("speed", intSpeed)
        updateIntent.putExtra("limit", limit)
        sendBroadcast(updateIntent)

        if (isOverspeed) {
            playOverspeedWarning(limit)
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
    // CLEANUP
    // ---------------------------------------------------------
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        fused.removeLocationUpdates(locationCallback)
        mediaPlayer?.release()
        speedometer?.hide()
        scope.cancel()
        sendBroadcast(Intent("SERVICE_STATUS").apply {
            putExtra("status", "stopped")
        })
        mediaBroadcast?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
