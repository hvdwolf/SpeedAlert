package xyz.hvdw.speedalert

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DrivingService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var settings: SettingsManager
    private lateinit var repo: SpeedLimitRepository
    private lateinit var notifier: ServiceNotification
    private lateinit var locationManager: LocationManager

    private var speedometer: FloatingSpeedometer? = null

    private var lastLimitFetchTime = 0L
    private var lastLimit = SpeedLimitResult(-1, -1, "none")
    private var lastBeepTime = 0L

    private var running = true

    private var lastSpeed = 0
    private var lastLocation: Location? = null
    private var lastGpsFixTime = 0L
    private var lastAccuracy = -1f

    private var beepPlayer: MediaPlayer? = null

    // RAW Kalman profile from AppConfig
    private val kalman = KalmanFilter(AppConfig.KALMAN_PROFILE)

    // single, shared GPS listener
    private val gpsListener = LocationListener { loc ->
        updateLocation(loc)
    }

    override fun onCreate() {
        super.onCreate()

        settings = SettingsManager(this)
        repo = SpeedLimitRepository(this)
        notifier = ServiceNotification(this)
        notifier.createChannel()

        beepPlayer = MediaPlayer.create(this, R.raw.beep)
        val vol = settings.getBeepVolume() // 0.0f – 1.0f
        beepPlayer?.setVolume(vol, vol)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // register GPS listener once
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            try {
                val provider = findGpsProvider()
                if (provider == null) {
                    log("No usable GPS provider found!")
                } else {
                    try {
                        locationManager.requestLocationUpdates(
                            provider,
                            200,
                            0f,
                            gpsListener
                        )
                        log("Using GPS provider: $provider")
                    } catch (e: Exception) {
                        log("GPS requestLocationUpdates failed: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                log("GPS requestLocationUpdates failed: ${e.message}")
            }
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
            locationManager.removeUpdates(gpsListener)
        } catch (_: Exception) {}

        speedometer?.hide()
        beepPlayer?.release()
        beepPlayer = null

        stopForeground(true)
        scope.cancel()

        log("Service: destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

                restartGpsIfNeeded()

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
    fun updateLocation(loc: Location) {
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

    private fun restartGpsIfNeeded() {
        val age = System.currentTimeMillis() - lastGpsFixTime

        if (age > 15000) {
            mainHandler.post {
                val provider = findGpsProvider()
                log("GPS watchdog: restarting using provider=$provider")

                try {
                    locationManager.removeUpdates(gpsListener)
                } catch (_: Exception) {}

                if (provider != null) {
                    try {
                        locationManager.requestLocationUpdates(
                            provider,
                            200,
                            0f,
                            gpsListener
                        )
                    } catch (e: Exception) {
                        log("GPS re-request failed: ${e.message}")
                    }
                }
            }

            lastGpsFixTime = System.currentTimeMillis()
        }
    }

    private fun findGpsProvider(): String? {
        val providers = locationManager.allProviders
        log("Available providers: $providers")

        return when {
            providers.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            providers.contains("fused") -> "fused"
            providers.contains("gps0") -> "gps0"
            providers.contains("bd_gps") -> "bd_gps"
            providers.contains("nmea") -> "nmea"
            providers.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    // ---------------------------------------------------------
    // SPEED LIMIT FETCHING (ASYNC)
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
                    log("Service: no valid speed limit (source=${fetched.source}) — keeping previous limit")
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
    // OVERLAY
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

            // Play immediately if this is the first beep OR 10 seconds have passed
            if (now - lastBeepTime >= 10_000) {

                val vol = settings.getBeepVolume()
                beepPlayer?.setVolume(vol, vol)

                if (beepPlayer?.isPlaying == false) {
                    beepPlayer?.start()
                }

                lastBeepTime = now
            }
        } else {
            // Reset when no longer overspeeding
            lastBeepTime = 0L
        }


        speedometer?.updateSpeed(lastSpeed, limit, overspeed)
    }

    // ---------------------------------------------------------
    // BROADCAST HELPERS
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

    private fun log(msg: String) {
        val intent = Intent("speedalert.debug").apply {
            putExtra("msg", msg)
        }
        sendBroadcast(intent)
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
