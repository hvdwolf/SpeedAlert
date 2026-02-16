package xyz.hvdw.speedalert

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

class DrivingService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var settings: SettingsManager
    private lateinit var repo: SpeedLimitRepository
    private lateinit var notifier: ServiceNotification

    // Fused location provider
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    private var speedometer: FloatingSpeedometer? = null

    private var lastLimitFetchTime = 0L
    private var lastLimit = SpeedLimitResult(-1, -1, "none")
    private var lastBeepTime = 0L

    private var running = true

    private var lastSpeed = 0
    private var lastLocation: Location? = null
    private var lastGpsFixTime = 0L
    private var lastAccuracy = -1f

    // SoundPool for beep
    private var soundPool: SoundPool? = null
    private var beepSoundId: Int = 0

    // Kalman filter
    private val kalman = KalmanFilter(AppConfig.KALMAN_PROFILE)

    override fun onCreate() {
        super.onCreate()

        settings = SettingsManager(this)
        repo = SpeedLimitRepository(this)
        notifier = ServiceNotification(this)
        notifier.createChannel()

        // -----------------------------
        // SOUNDPOOL INITIALIZATION
        // -----------------------------
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        beepSoundId = soundPool!!.load(this, R.raw.beep, 1)

        // -----------------------------
        // FUSED LOCATION SETUP
        // -----------------------------
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        )
            .setMinUpdateIntervalMillis(500)
            .setMaxUpdateDelayMillis(2000)
            .build()

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            log("FusedLocationProvider: started")
        } else {
            log("GPS permission not granted in service")
        }

        log("Service: created")

        startForeground(1, notifier.createNotification())

        scope.launch {
            mainLoop()
        }
    }

    override fun onDestroy() {
        running = false

        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}

        speedometer?.hide()

        soundPool?.release()
        soundPool = null

        stopForeground(true)
        scope.cancel()

        log("Service: destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------
    // FUSED LOCATION CALLBACK
    // ---------------------------------------------------------
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) {
                updateLocation(loc)
            }
        }
    }

    // ---------------------------------------------------------
    // MAIN LOOP
    // ---------------------------------------------------------
    private suspend fun mainLoop() {
        while (running) {
            try {
                val loc = lastLocation
                if (loc != null) {
                    scheduleSpeedLimitFetch()
                    updateLocationAndSpeed()
                }

                mainHandler.post { updateOverlay() }

            } catch (e: Exception) {
                log("mainLoop error: ${e.message}")
            }

            delay(1000)
        }
    }

    // ---------------------------------------------------------
    // LOCATION + SPEED
    // ---------------------------------------------------------
    private fun updateLocation(loc: Location) {
        lastLocation = loc
        lastGpsFixTime = System.currentTimeMillis()
        lastAccuracy = loc.accuracy
        log("GPS fix: lat=${loc.latitude}, lon=${loc.longitude}, acc=${loc.accuracy}m")
    }

    private fun updateLocationAndSpeed() {
        val loc = lastLocation ?: return

        val rawSpeed = (loc.speed * 3.6).toInt()
        val filtered = kalman.update(rawSpeed.toDouble()).toInt()

        lastSpeed = filtered
        sendSpeedBroadcast(filtered, lastLimit.limitKmh)
    }

    private fun hasGpsFix(): Boolean {
        val age = System.currentTimeMillis() - lastGpsFixTime
        return age < 5000
    }

    // ---------------------------------------------------------
    // SPEED LIMIT FETCHING
    // ---------------------------------------------------------
    private var limitJob: Job? = null

    private fun scheduleSpeedLimitFetch() {
        val loc = lastLocation ?: return
        val lat = loc.latitude
        val lon = loc.longitude
        val now = System.currentTimeMillis()

        if (now - lastLimitFetchTime < 4000) return
        lastLimitFetchTime = now

        limitJob?.cancel()
        limitJob = scope.launch(Dispatchers.IO) {
            try {
                val acc = lastAccuracy
                val radius = dynamicRadius(acc)
                log("Service: calling repo for $lat,$lon")
                val fetched = repo.getSpeedLimit(lat, lon, radius)
                log("Service: repo returned $fetched")

                if (fetched.limitKmh > 0) {
                    lastLimit = fetched
                } else {
                    log("Service: no valid speed limit — keeping previous")
                }
            } catch (e: Exception) {
                log("SpeedLimit fetch failed: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------
    // OVERSPEED CALCULATION
    // ---------------------------------------------------------
    private fun calculateOverspeed(limit: Int, speed: Int): Boolean {
        if (limit <= 0) return false

        return if (settings.isOverspeedModePercentage()) {
            val pct = settings.getOverspeedPercentage()
            val allowed = limit + (limit * pct / 100)
            speed > allowed
        } else {
            val fixed = settings.getOverspeedFixedKmh()
            val allowed = limit + fixed
            speed > allowed
        }
    }

    // ---------------------------------------------------------
    // OVERLAY + BEEP
    // ---------------------------------------------------------
    private fun updateOverlay() {
        if (!settings.getShowSpeedometer()) {
            speedometer?.hide()
            speedometer = null
            return
        }

        if (speedometer == null) {
            speedometer = FloatingSpeedometer(this, settings)
            speedometer?.show()
            log("Service: overlay shown")
        }

        if (!hasGpsFix()) {
            speedometer?.showNoGps()
            return
        }

        val limit = lastLimit.limitKmh
        val overspeed = calculateOverspeed(limit, lastSpeed)

        if (overspeed) {
            val now = System.currentTimeMillis()

            if (now - lastBeepTime >= 10_000) {
                val vol = settings.getBeepVolume()
                soundPool?.play(beepSoundId, vol, vol, 1, 0, 1f)
                lastBeepTime = now
            }
        } else {
            lastBeepTime = 0L
        }

        speedometer?.updateSpeed(lastSpeed, limit, overspeed)
    }

    // ---------------------------------------------------------
    // BROADCAST
    // ---------------------------------------------------------
    private fun sendSpeedBroadcast(speed: Int, limit: Int) {
        val cleanLimit = if (limit > 0) limit else -1
        val overspeed = calculateOverspeed(cleanLimit, speed)

        val intent = Intent("xyz.hvdw.speedalert.SPEED_UPDATE").apply {
            setPackage(packageName)
            putExtra("speed", speed)
            putExtra("limit", cleanLimit)
            putExtra("overspeed", overspeed)
            putExtra("accuracy", lastAccuracy)
        }
        sendBroadcast(intent)
    }

    // ---------------------------------------------------------
    // LOGGING
    // ---------------------------------------------------------
    private val tsFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private fun log(msg: String) {
        val ts = LocalDateTime.now().format(tsFormat)
        val msgline = "$ts $msg"

        val intent = Intent("speedalert.debug").apply {
            putExtra("msg", msgline)
            setPackage(packageName)
        }
        sendBroadcast(intent)

        val file = File(filesDir, "speedalert.log")
        file.appendText(msgline + "\n")
    }

    private fun dynamicRadius(acc: Float): Int {
        return when {
            acc <= 2.5f -> 10
            acc <= 5f   -> 15
            acc <= 10f  -> 20
            else        -> 25
        }
    }
}
