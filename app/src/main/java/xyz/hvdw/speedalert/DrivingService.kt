package xyz.hvdw.speedalert

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer
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
    private var lastLimit: Int? = null
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

        // Android 13+ requires POST_NOTIFICATIONS permission
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        // Foreground notification
        val notif = ServiceNotification(this)
        notif.createChannel()
        startForeground(NOTIF_ID, notif.createNotification())

        // Overlay
        if (settings.getShowSpeedometer()) {
            speedometer = FloatingSpeedometer(this, settings)
            speedometer?.show()
        }

        initMediaPlayer()
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_OVERLAY -> {
                speedometer?.updateStyle(settings)
            }
        }
        return START_STICKY
    }

    private fun initMediaPlayer() {
        val custom = settings.getCustomSound()
        mediaPlayer = if (custom != null) {
            MediaPlayer.create(this, custom)
        } else {
            MediaPlayer.create(this, R.raw.beep)
        }
    }

    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L   // 1000 ms = 1 seconds
        )
            .setMinUpdateDistanceMeters(2f)  // minimum distance 2 meters
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

    private suspend fun handleLocation(loc: Location) {
        val lat = loc.latitude
        val lon = loc.longitude
        //val speedKmh = loc.speed * 3.6
        //val intSpeed = speedKmh.toInt()
        val rawSpeed = loc.speed * 3.6
        val filteredSpeed = kalman.update(rawSpeed)
        val intSpeed = filteredSpeed.toInt()

        val limit = repo.getSpeedLimit(lat, lon)

        if (limit == null) {
            speedometer?.updateSpeed(intSpeed, null, false)
            return
        }

        lastLimit = limit

        val overshootPercent = settings.getOverspeedPercentage()
        val threshold = limit * (1 + overshootPercent / 100.0)
        val isOverspeed = filteredSpeed> threshold

        speedometer?.updateSpeed(intSpeed, limit, isOverspeed)

        if (isOverspeed) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@DrivingService,
                    getString(R.string.overspeed_warning, limit),
                    Toast.LENGTH_SHORT
                ).show()
            }

            mediaPlayer?.let { if (!it.isPlaying) it.start() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        fused.removeLocationUpdates(locationCallback)
        mediaPlayer?.release()
        speedometer?.hide()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
