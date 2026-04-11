package xyz.hvdw.speedalert

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
//import androidx.preference.PreferenceManager
import android.content.SharedPreferences
import com.google.android.gms.location.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.*
import kotlin.math.roundToInt


class DrivingService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val mphCountries = setOf("GB", "LR", "MM", "US")

    private lateinit var settings: SettingsManager
    private lateinit var repo: SpeedLimitRepository
    private lateinit var notifier: ServiceNotification
    private lateinit var localDb: LocalSpeedDbManager

    // Hybrid providers
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var locationRequest: LocationRequest

    private var speedometer: FloatingSpeedometer? = null
    private var toastOverlay: FloatingToastOverlay? = null

    private var lastLimitFetchTime = 0L
    private var lastLimit = SpeedLimitResult(-1, -1, "none")
    private var lastBeepTime = 0L
    private var lastCameraStage = 0 // 0 = none, 1 = 200m, 2 = 150m, 3 = 50m
    private var currentCameraStage = 0
    private var lastCameraDistance = Double.MAX_VALUE
    private var lastCountryUpdateTime = 0L

    private var running = true

    private var lastSpeed = 0
    private var lastLocation: Location? = null
    private var lastGpsFixTime = 0L
    private var lastAccuracy = -1f

    // Store last fetch location + speed
    private var lastFetchLocation: Location? = null
    private var lastFetchSpeed: Int = 0

    // Audiotrack for code generated beeps
    private var twoToneTrack: AudioTrack? = null
    private var tripleBeepTrack: AudioTrack? = null

    // Kalman filter
    private val kalman = KalmanFilter(AppConfig.KALMAN_PROFILE)

    // For speed camera
    private var cameraManager: CameraManager? = null
    private var lastCameraBeepTime = 0L
    private lateinit var ttsManager: TTSManager


    override fun onCreate() {
        super.onCreate()

        settings = SettingsManager(this)
        repo = SpeedLimitRepository(this)
        notifier = ServiceNotification(this)
        notifier.createChannel()

        //speedometer = FloatingSpeedometer(this, settings)
        toastOverlay = FloatingToastOverlay(this)

        localDb = LocalSpeedDbManager(this)
        localDb.initialize()

        cameraManager = localDb.getCameraManager()
        ttsManager = TTSManager(this, settings)

        // -----------------------------
        // AUDIOTRACK INITIALIZATION
        // -----------------------------
        initTwoToneBeep()
        initTripleBeep()
       

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

        tripleBeepTrack?.release()
        tripleBeepTrack = null
        twoToneTrack?.release()
        twoToneTrack = null
        ttsManager.shutdown()

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
                    checkCameras()
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

        updateCountryCodeIfNeeded(loc.latitude, loc.longitude)
    }

    private fun updateLocationAndSpeed() {
        val loc = lastLocation ?: return

        // GPS ALWAYS returns m/s on your device
        val speedKmh = (loc.speed * 3.6).roundToInt()

        val filtered = kalman.update(speedKmh.toDouble()).roundToInt()

        lastSpeed = filtered   // ALWAYS store km/h internally
        sendSpeedBroadcast(filtered, lastLimit.roadLimitWithoutUnit)
    }

    private fun hasGpsFix(): Boolean {
        val age = System.currentTimeMillis() - lastGpsFixTime
        return age < 5000 && lastAccuracy in 0f..25f
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
                log("Skipping fetch: moved only ${dist.roundToInt()}m (< $minDist m)")
                return
            }
        }

        // Passed both checks → update timestamps
        lastLimitFetchTime = now
        lastFetchLocation = loc
        lastFetchSpeed = lastSpeed

        // Cancel previous job (but it may still run a few lines!)
        limitJob?.cancel()

        limitJob = scope.launch(Dispatchers.IO) {

            // If this job is cancelled before starting, bail out
            if (!isActive) return@launch

            // Snapshot: we NEVER lose this unless we get a better one
            val previousLimit = lastLimit
            var candidate: SpeedLimitResult? = null

            try {
                val acc = lastAccuracy
                val radius = dynamicRadius(acc)
                val country = settings.getCountryCode()
                log("Using stored country: ${country ?: "none"}")

                // ---------------------------------------------------------
                // 1. LOCAL DB LOOKUP
                // ---------------------------------------------------------
                if (country != null) {
                    localDb.updateCountry(country)
                    val localSpeed = localDb.lookupSpeed(lat, lon)

                    if (!isActive) return@launch

                    if (localSpeed != null && localSpeed > 0) {
                        candidate = SpeedLimitResult(
                            speedKmh = -1,
                            roadLimitWithoutUnit = localSpeed,
                            source = "localdb:${country.lowercase()}"
                        )
                        log("Local DB hit: $candidate")
                    } else {
                        log("Local DB miss for country=$country")
                    }
                }

                // ---------------------------------------------------------
                // 2. OVERPASS (only if DB did NOT give a value)
                // ---------------------------------------------------------
                if (candidate == null) {
                    log("Calling repo for $lat,$lon (radius=$radius)")
                    val fetched = repo.getSpeedLimit(lat, lon, radius)
                    log("Repo returned: $fetched")

                    if (!isActive) return@launch

                    if (fetched.roadLimitWithoutUnit > 0) {
                        candidate = fetched
                        log("Repo hit: $candidate")
                    } else {
                        log("Repo returned no valid speed limit")
                    }
                }

                // ---------------------------------------------------------
                // 3. FALLBACK (only if still nothing AND enabled)
                // ---------------------------------------------------------
                if (candidate == null && settings.useCountryFallback()) {
                    val fbCountry = settings.getCountryCode() ?: country
                    val fb = CountrySpeedFallbacks.get(fbCountry)

                    if (!isActive) return@launch

                    if (fb == null) {
                        log("Fallback table missing for $fbCountry — keeping previous limit")
                    } else {
                        val chosen = when {
                            lastSpeed < 60 -> fb.urban
                            lastSpeed < 90 -> fb.rural
                            lastSpeed < 120 -> fb.divided
                            else -> fb.motorway
                        }

                        if (chosen > 0) {
                            candidate = SpeedLimitResult(
                                speedKmh = -1,
                                roadLimitWithoutUnit = chosen,
                                source = "fallback:${fbCountry ?: "unknown"}"
                            )
                            log("Fallback applied: $candidate")
                        } else {
                            log("Fallback produced non‑positive value — ignoring")
                        }
                    }
                } else if (candidate == null && !settings.useCountryFallback()) {
                    log("Fallback disabled — keeping previous speed limit")
                }

            } catch (e: Exception) {
                log("SpeedLimit fetch failed: ${e.message}")
            }

            // ---------------------------------------------------------
            // FINAL COMMIT (atomic, race‑safe)
            // ---------------------------------------------------------
            if (!isActive) return@launch

            val finalLimit =
                if (candidate != null && candidate!!.roadLimitWithoutUnit > 0) {
                    candidate!!
                } else {
                    log("No valid new limit — keeping previous: $previousLimit")
                    previousLimit
                }

            lastLimit = finalLimit
            log("Final lastLimit after fetch: $lastLimit")
        }
    }


    // ---------------------------------------------------------
    // OVERSPEED CALCULATION
    // ---------------------------------------------------------

    private fun calculateOverspeed(limit: Int, speedKmh: Int): Boolean {
        if (limit <= 0) return false

        // Convert speed to same unit as limit
        val speed = if (settings.usesMph()) {
            (speedKmh * 0.621371).roundToInt()   // km/h → mph
        } else {
            speedKmh
        }

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

            // Restore camera stage immediately
            speedometer?.updateCameraStage(currentCameraStage)

            log("Service: overlay shown")
        }

        if (!hasGpsFix()) {
            speedometer?.showNoGps()
            return
        }

        val displaySpeed = speedToDisplay(lastSpeed)
        // If we have no valid speed limit yet, keep showing grey sign
        if (lastLimit.roadLimitWithoutUnit <= 0) {
            if (settings.useSignOverlay()) {
                speedometer?.updateSpeedSignMode(displaySpeed, -1, false)
            } else {
                speedometer?.updateSpeedTextMode(displaySpeed, -1, false)
            }
            return
        }


        //val loc = lastLocation
        val country = settings.getCountryCode()

        val roadLimitWithoutUnit = lastLimit.roadLimitWithoutUnit
        val displayLimit = limitToDisplay(lastLimit.roadLimitWithoutUnit, country)

        log("updateOverlay: displayLimit=$displayLimit, country=$country, source=${lastLimit.source}")
        val overspeed = calculateOverspeed(roadLimitWithoutUnit, lastSpeed)  // still in km/h internally

        if (overspeed) {
            val now = System.currentTimeMillis()

            val shouldBeep =
                if (settings.beepOnce()) {
                    // Only beep once per overspeed event
                    lastBeepTime == 0L
                } else {
                    // Beep every 10 seconds
                    now - lastBeepTime >= 10_000
                }

            if (shouldBeep) {
                val vol = settings.getBeepVolume()
                if (!settings.isMuted()) {
                    tripleBeepTrack?.setVolume(vol)
                    tripleBeepTrack?.setStereoVolume(vol, vol)
                    playTripleBeep()
                }
                lastBeepTime = now
            }

        } else {
            // Reset so next overspeed event can beep again
            lastBeepTime = 0L
        }

        // Store DB names once they become available
        localDb.getActiveSpeedDbName()?.let { name ->
            if (settings.getSpeedLimitDbName() != name) {
                settings.setSpeedLimitDbName(name)
                log("Stored speed DB name: $name")
            }
        }

        localDb.getCameraDbName()?.let { name ->
            if (settings.getCameraDbName() != name) {
                settings.setCameraDbName(name)
                log("Stored camera DB name: $name")
            }
        }

        if (settings.useSignOverlay()) {
            speedometer?.updateSpeedSignMode(displaySpeed, displayLimit, overspeed)
        } else {
            speedometer?.updateSpeedTextMode(displaySpeed, displayLimit, overspeed)
        }
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
            acc <= 1.5f -> 15
            acc <= 2.5f -> 20
            acc <= 5f   -> 30
            acc <= 10f  -> 40
            else        -> 50
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


    // ---------------------------------------------------------
    // CONVERT GPS SPEED TO USER PREFERENCE
    // ---------------------------------------------------------
    // lastSpeed is ALWAYS km/h internally
    // It is always calculated from GPS m/s to kmh
    private fun speedToDisplay(speedKmh: Int): Int {
        if (speedKmh <= 0) return speedKmh

        return if (settings.usesMph()) {
            (speedKmh * 0.621371).roundToInt()   // km/h → mph
        } else {
            speedKmh                        // km/h → km/h
        }
    }

    // ---------------------------------------------------------
    // OVERPASS/DB RETURN SPEEDLIMIT IN LOCAL UNIT
    // ---------------------------------------------------------
    // limitWithoutUnit is the raw Overpass/DB value (unitless)
    private fun limitToDisplay(limitWithoutUnit: Int, country: String?): Int {

        if (limitWithoutUnit <= 0) return limitWithoutUnit

        val isMphCountry = country != null && mphCountries.contains(country.uppercase())
        val userWantsMph = settings.usesMph()  // or your existing flag

        return when {
            // kmh country + kmh user
            !isMphCountry && !userWantsMph -> limitWithoutUnit  // already kmh

            // mph country + mph user
            isMphCountry && userWantsMph -> limitWithoutUnit    // already mph

            // kmh country + mph user
            !isMphCountry && userWantsMph -> (limitWithoutUnit * 0.621371).roundToInt()  // is now converted: kmh -> mph

            // mph country + kmh user
            isMphCountry && !userWantsMph -> (limitWithoutUnit / 0.621371).roundToInt()  // is now converted: mph -> kmh

            else -> limitWithoutUnit
        }
    }

    // ---------------------------------------------------------
    // COUNTRY DETECTION with caching for better performance
    // ---------------------------------------------------------
    private fun updateCountryCodeIfNeeded(lat: Double, lon: Double) {
        val now = System.currentTimeMillis()

        // Only update every 5 minutes
        if (now - lastCountryUpdateTime < 5 * 60 * 1000) return  // run every 3 minutes

        lastCountryUpdateTime = now

        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val results = geocoder.getFromLocation(lat, lon, 1)

            val countryCode = results?.firstOrNull()?.countryCode
            if (countryCode != null) {
                val sm = SettingsManager(this)
                sm.setCountryCode(countryCode)
                log("updateCountryCodeIfNeeded: Country code updated: $countryCode")
            }
        } catch (e: Exception) {
            log("Failed to update country code: ${e.message}")
        }
    }



    // ---------------------------------------------------------
    // USE AUDIOTRACK INSTEAD OF SOUNDPOOL. THIS SHOULD BYPASS
    // ALL NEGATIVE FYT/DUDU AUDIOSTACK "FEATURES".
    // USE THE ToneGenerator.kt FOR THIS
    // ---------------------------------------------------------
    // Below for speed camera
    private fun initTwoToneBeep() {
        val gen = ToneGenerator()
        val attrs = settings.getAudioAttributes()

        val tone1 = gen.generateTone(freqHz = 1000.0, durationMs = 70)
        val gap   = gen.generateSilence(durationMs = 15)
        val tone2 = gen.generateTone(freqHz = 1400.0, durationMs = 70)

        twoToneTrack = gen.buildTrack(
            attrs,
            tone1,
            gap,
            tone2
        )
    }

    fun playTwoToneBeep() {
        twoToneTrack?.stop()
        twoToneTrack?.reloadStaticData()
        twoToneTrack?.play()
    }



    // Initialize triple beep for speed limit
    private fun initTripleBeep() {
        val gen = ToneGenerator()
        val attrs = settings.getAudioAttributes()

        val tone = gen.generateTone(freqHz = 1870.0, durationMs = 160)
        val gap = gen.generateSilence(durationMs = 25)

        tripleBeepTrack = gen.buildTrack(
            attrs,
            tone, gap,
            tone, gap,
            tone
        )
    }

    fun playTripleBeep() {
        tripleBeepTrack?.stop()
        tripleBeepTrack?.reloadStaticData()
        tripleBeepTrack?.play()
    }



    private fun checkCameras() {
        val loc = lastLocation ?: return
        val camMgr = cameraManager ?: return

        val heading = loc.bearing
        val lat = loc.latitude
        val lon = loc.longitude

        val cams = camMgr.findNearbyCameras(lat, lon, heading, 300.0)
        //val cams = camMgr.findNearbyCameras(lat, lon, null, 300.0)
        if (cams.isEmpty()) {
            resetCameraWarning()
            return
        }

        val nearest = cams.first()
        val dist = nearest.distanceMeters
        log("checkCameras: dist=${nearest.distanceMeters}")

        // ---------------------------------------------------------
        // 1. Compute bearing to camera (robust wrap-around)
        // ---------------------------------------------------------
        val camLoc = android.location.Location("").apply {
            latitude = nearest.lat
            longitude = nearest.lon
        }

        val bearingToCamera = loc.bearingTo(camLoc)

        // Normalize both to 0..360
        val h = (heading + 360) % 360
        val b = (bearingToCamera + 360) % 360

        // Compute smallest angle difference
        val diff = Math.abs(h - b)
        val angleDiff = if (diff > 180) 360 - diff else diff

        val isAhead = angleDiff < 60 

        if (!isAhead) {
            resetCameraWarning()
            return
        }

        // ---------------------------------------------------------
        // 2. Reset only if CLEARLY moving away (50m threshold)
        // ---------------------------------------------------------
        if (dist > lastCameraDistance + 50) {
            resetCameraWarning()
            currentCameraStage = 0
            lastCameraStage = 0
            speedometer?.updateCameraStage(0)

            lastCameraDistance = dist

            return
        }

        lastCameraDistance = dist

        // ---------------------------------------------------------
        // 3. Approaching stages
        // ---------------------------------------------------------
        if (dist in 200.0..300.0 && lastCameraStage < 1) {
            triggerCameraAlert(nearest, dist)
            //TTS message
            if (settings.getSpeakTtsSpeedcamWarning()) {
                ttsManager.speak(R.string.tts_speedcam_warning)
            }
            //ttsManager.speak(R.string.tts_speedcam_warning)
            currentCameraStage = 1
            //speedometer?.updateCameraStage(1)
            lastCameraStage = 1
            return
        }

        if (dist in 135.0..165.0 && lastCameraStage < 2) {
            triggerCameraAlert(nearest, dist)
            currentCameraStage = 2
            //speedometer?.updateCameraStage(2)
            lastCameraStage = 2
            return
        }

        if (dist in 0.0..70.0 && lastCameraStage < 3) {
            triggerCameraAlert(nearest, dist)
            currentCameraStage = 3
            //speedometer?.updateCameraStage(3)
            lastCameraStage = 3
            return
        }

        if (dist > 300.0) {
            currentCameraStage = 0
            lastCameraStage = 0
            //speedometer?.updateCameraStage(0)
        }

    }


    private fun triggerCameraAlert(cam: CameraHit, dist: Double) {
        val vol = settings.getBeepVolume()
        /*val muted = if (settings.showSpeakerMuteButton()) {
            settings.isMuted()
        } else {
            false   // mute button hidden → always unmuted
        }*/
        if (!settings.isMuted()) {
            twoToneTrack?.setVolume(vol)
            playTwoToneBeep()
        }

        val meters = dist.roundToInt()
        val msg = getString(R.string.camera_alert, meters)

        log("Camera alert: ${cam.type} at ${meters}m")

        Handler(Looper.getMainLooper()).post {
            toastOverlay?.show(msg, meters)
        }

    }

    private fun resetCameraWarning() {
        lastCameraStage = 0
        currentCameraStage = 0
        lastCameraDistance = Double.MAX_VALUE
        //speedometer?.updateCameraStage(0)
    }

}
