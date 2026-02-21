package xyz.hvdw.speedalert

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
    private lateinit var localDb: LocalSpeedDbManager

    // Hybrid providers
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
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

    // Store last fetch location + speed
    private var lastFetchLocation: Location? = null
    private var lastFetchSpeed: Int = 0

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

        localDb = LocalSpeedDbManager(this)
        localDb.initialize()

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
        // HYBRID GPS SETUP
        // -----------------------------
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        )
            .setMinUpdateIntervalMillis(1000)
            .setMaxUpdateDelayMillis(0)
            .build()

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            // PRIMARY: dynamic GPS provider detection
            val provider = findGpsProvider()

            if (provider != null) {
                try {
                    locationManager.requestLocationUpdates(
                        provider,
                        200,
                        0f,
                        gpsListener
                    )
                    log("GPS provider started: $provider")
                } catch (e: Exception) {
                    log("GPS provider '$provider' failed: ${e.message}")
                }
            } else {
                log("No usable GPS provider found! Falling back to fused only.")
            }

            // SECONDARY: Fused
            try {
                fusedClient.requestLocationUpdates(
                    locationRequest,
                    fusedCallback,
                    Looper.getMainLooper()
                )
                log("FusedLocationProvider started")
            } catch (e: Exception) {
                log("Fused start failed: ${e.message}")
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

        try {
            fusedClient.removeLocationUpdates(fusedCallback)
        } catch (_: Exception) {}

        speedometer?.hide()

        soundPool?.release()
        soundPool = null

        localDb.close()

        stopForeground(STOP_FOREGROUND_REMOVE)
        scope.cancel()

        log("Service: destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------
    // PRIMARY GPS LISTENER
    // ---------------------------------------------------------
    private val gpsListener = android.location.LocationListener { loc ->
        updateLocation(loc)
    }

    // ---------------------------------------------------------
    // SECONDARY FUSED CALLBACK
    // ---------------------------------------------------------
    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val gpsAge = System.currentTimeMillis() - lastGpsFixTime

            // Only use fused if GPS is stale (> 5 sec)
            if (gpsAge > 5000) {
                for (loc in result.locations) {
                    updateLocation(loc)
                }
            }
        }
    }

    // ---------------------------------------------------------
    // MAIN LOOP
    // ---------------------------------------------------------
    private suspend fun mainLoop() {
        while (running) {
            try {
                restartGpsIfNeeded()

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
        return age < 3000 && lastAccuracy in 0f..25f
    }

    // ---------------------------------------------------------
    // GPS WATCHDOG (main-thread safe)
    // ---------------------------------------------------------
    private fun restartGpsIfNeeded() {
        val age = System.currentTimeMillis() - lastGpsFixTime

        if (age > 8000) {
            log("GPS watchdog: restarting GPS provider")

            mainHandler.post {
                try {
                    locationManager.removeUpdates(gpsListener)
                } catch (_: Exception) {}

                val provider = findGpsProvider()

                if (provider != null) {
                    try {
                        locationManager.requestLocationUpdates(
                            provider,
                            200,
                            0f,
                            gpsListener
                        )
                        log("GPS provider restarted: $provider")
                    } catch (e: Exception) {
                        log("GPS restart failed: ${e.message}")
                    }
                } else {
                    log("GPS restart failed: no provider available")
                }
            }
        }
    }

    // ---------------------------------------------------------
    // COUNTRY DETECTION
    // ---------------------------------------------------------
    private fun getCountryCode(lat: Double, lon: Double): String? {
        return try {
            val geocoder = android.location.Geocoder(this)
            val list = geocoder.getFromLocation(lat, lon, 1)
            list?.firstOrNull()?.countryCode
        } catch (e: Exception) {
            log("Geocoder failed: ${e.message}")
            null
        }
    }

    // ---------------------------------------------------------
    // SPEED LIMIT FETCHING + LOCAL DB + FALLBACKS
    // ---------------------------------------------------------
    private var limitJob: Job? = null

    private fun scheduleSpeedLimitFetch() {
        val loc = lastLocation ?: return
        val now = System.currentTimeMillis()
        val lat = loc.latitude
        val lon = loc.longitude

        // 1. Interval check
        val interval = settings.getSpeedLimitFetchIntervalMs()
        if (now - lastLimitFetchTime < interval) return

        // 2. Distance check
        val minDist = settings.getMinDistanceForFetch()
        lastFetchLocation?.let { prev ->
            val dist = prev.distanceTo(loc)
            if (dist < minDist) {
                log("Skipping fetch: moved only ${dist.toInt()}m (< $minDist m)")
                return
            }
        }

        // Passed both checks → update timestamps
        lastLimitFetchTime = now
        lastFetchLocation = loc
        lastFetchSpeed = lastSpeed

        limitJob?.cancel()
        limitJob = scope.launch(Dispatchers.IO) {
            try {
                val acc = lastAccuracy
                val radius = dynamicRadius(acc)

                // First: try local DB based on country code
                val country = getCountryCode(lat, lon)
                log("Country detected: ${country ?: "none"}")
                if (country != null) {
                    localDb.updateCountry(country)
                    val localSpeed = localDb.lookupSpeed(lat, lon)
                    if (localSpeed != null && localSpeed > 0) {
                        log("Local DB hit: speed=$localSpeed for country=$country")
                        lastLimit = SpeedLimitResult(
                            speedKmh = -1,
                            limitKmh = localSpeed,
                            source = "localdb:${country.lowercase()}"
                        )
                        log("Local DB hit: $lastLimit")
                        return@launch
                    } else {
                        log("Local DB miss or no DB for country: $country")
                    }
                } else {
                    log("No country code available for local DB lookup")
                }

                // If no local DB result → use repo (Overpass/Nominatim)
                log("Service: calling repo for $lat,$lon (radius=$radius)")
                val fetched = repo.getSpeedLimit(lat, lon, radius)
                log("Service: repo returned $fetched")

                if (fetched.limitKmh > 0) {
                    lastLimit = fetched
                } else {
                    log("Service: no valid speed limit — applying fallback")

                    val fallbackCountry = country ?: getCountryCode(lat, lon)
                    val fb = CountrySpeedFallbacks.get(fallbackCountry)

                    val chosen = when {
                        lastSpeed < 60 -> fb.urban
                        lastSpeed < 90 -> fb.rural
                        lastSpeed < 120 -> fb.divided
                        else -> fb.motorway
                    }

                    lastLimit = SpeedLimitResult(
                        speedKmh = -1,
                        limitKmh = chosen,
                        source = "fallback:${fallbackCountry ?: "unknown"}"
                    )

                    log("Fallback applied: $lastLimit")
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

    fun logExternal(msg: String) {
        log(msg)
    }


    private fun dynamicRadius(acc: Float): Int {
        return when {
            acc <= 1.5f -> 7
            acc <= 2.5f -> 10
            acc <= 5f   -> 15
            acc <= 10f  -> 20
            else        -> 25
        }
    }

    // ---------------------------------------------------------
    // DYNAMIC GPS PROVIDER DETECTION (FYT / Chinese units)
    // ---------------------------------------------------------
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
}
