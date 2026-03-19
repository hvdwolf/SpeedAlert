package xyz.hvdw.speedalert

import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var swShowOverlay: Switch
    private lateinit var swBroadcast: Switch
    private lateinit var swOverspeedMode: Switch
    private lateinit var swUseMph: Switch
    private lateinit var seekOverspeed: SeekBar
    private lateinit var txtOverspeedLabel: TextView
    private lateinit var seekBeepVolume: SeekBar
    private lateinit var txtBeepVolumeLabel: TextView
    private lateinit var btnTestBeep: Button
    private lateinit var seekBrightness: SeekBar
    private lateinit var seekSpeedoSize: SeekBar
    private lateinit var txtSpeedoSizeValue: TextView
    private lateinit var switchSign: Switch
    private lateinit var switchHideCurrentSpeed: Switch
    private lateinit var swUseCountryFallback: Switch

    private lateinit var spinnerFetchInterval: Spinner
    private lateinit var spinnerMinDistance: Spinner

    private lateinit var swAutoStart: Switch
    private lateinit var swMinimizeOnStart: Switch

    private lateinit var seekBar: SeekBar
    private lateinit var preview: FrameLayout

    //private val prefs by lazy {
    //    getSharedPreferences("settings", Context.MODE_PRIVATE)
    //}
    private lateinit var prefs: SharedPreferences
    private lateinit var settings: SettingsManager


    private val prefsAuto by lazy {
        getSharedPreferences("speedalert_prefs", MODE_PRIVATE)
    }

    private val sizeLabels by lazy {
        arrayOf(
            getString(R.string.speedo_size_smallest),
            getString(R.string.speedo_size_smaller),
            getString(R.string.speedo_size_default),
            getString(R.string.speedo_size_bigger),
            getString(R.string.speedo_size_biggest)
        )
    }

    private val sizeScales = floatArrayOf(
        0.60f,
        0.80f,
        1.00f,
        1.20f,
        1.40f
    )

    private var testBeepId: Int = 0
    private var testTripleBeep: AudioTrack? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Tone generator to test triple beep
        val gen = ToneGenerator()
        val tone = gen.generateTone(1870.0, 160)
        val gap = gen.generateSilence(25)

        testTripleBeep = gen.buildTrack(
            tone, gap,
            tone, gap,
            tone
        )

        // Settings manager for all app settings
        settings = SettingsManager(this)
        // For all overlay UI sttings
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        val separator0 = findViewById<View>(R.id.settingsSeparator0)
        val separator1 = findViewById<View>(R.id.settingsSeparator1)
        val separator2 = findViewById<View>(R.id.settingsSeparator2)
        val separator3 = findViewById<View>(R.id.settingsSeparator3)
        val separator4 = findViewById<View>(R.id.settingsSeparator4)
        val separatorDayColor = getColor(R.color.separator_day)
        val separatorNightColor = getColor(R.color.separator_night)
        val separatorBaseColor = if (isNightMode()) separatorNightColor else separatorDayColor
        separator0.setBackgroundColor(separatorBaseColor)
        separator1.setBackgroundColor(separatorBaseColor)
        separator2.setBackgroundColor(separatorBaseColor)
        separator3.setBackgroundColor(separatorBaseColor)
        separator4.setBackgroundColor(separatorBaseColor)



        // ---------------------------------------------------------
        // FIND VIEWS
        // ---------------------------------------------------------
        swShowOverlay = findViewById(R.id.swShowOverlay)
        swBroadcast = findViewById(R.id.swBroadcast)
        swOverspeedMode = findViewById(R.id.swOverspeedMode)
        seekOverspeed = findViewById(R.id.seekOverspeed)
        txtOverspeedLabel = findViewById(R.id.txtOverspeedLabel)
        seekBeepVolume = findViewById(R.id.seekBeepVolume)
        txtBeepVolumeLabel = findViewById(R.id.txtBeepVolumeLabel)
        btnTestBeep = findViewById(R.id.btnTestBeep)
        seekBrightness = findViewById(R.id.seekBrightness)
        seekSpeedoSize = findViewById(R.id.seekSpeedoSize)
        txtSpeedoSizeValue = findViewById(R.id.txtSpeedoSizeValue)
        swUseMph = findViewById(R.id.swUseMph)
        switchSign = findViewById<Switch>(R.id.switchSignOverlay)
        switchHideCurrentSpeed = findViewById(R.id.switchHideCurrentSpeed)

        val swShowMute = findViewById<Switch>(R.id.swShowMuteButton)
        swShowMute.isChecked = settings.showSpeakerMuteButton()

        spinnerFetchInterval = findViewById(R.id.spinnerFetchInterval)
        spinnerMinDistance = findViewById(R.id.spinnerMinDistance)

        swAutoStart = findViewById(R.id.switchAutoStart)
        swMinimizeOnStart = findViewById(R.id.switchMinimizeOnStart)
        swShowMute.setOnCheckedChangeListener { _, checked ->
            settings.setShowSpeakerMuteButton(checked)

            // If user hides the mute button, force unmuted state
            if (!checked) {
                settings.setMuted(false)
            }
        }

        findViewById<Button>(R.id.btnMphInfo).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.mph_info_title))
                .setMessage(getString(R.string.mph_info_message))
                .setPositiveButton("OK", null)
                .show()
        }

        swUseCountryFallback = findViewById(R.id.swUseCountryFallback)
        swUseCountryFallback.isChecked = settings.useCountryFallback()
        swUseCountryFallback.setOnCheckedChangeListener { _, checked ->
            settings.setUseCountryFallback(checked)
        }


        // ---------------------------------------------------------
        // SHOW OVERLAY
        // ---------------------------------------------------------
        swShowOverlay.isChecked = settings.getShowSpeedometer()
        swShowOverlay.setOnCheckedChangeListener { _, checked ->
            settings.setShowSpeedometer(checked)
        }

        switchSign.isChecked = settings.useSignOverlay()
        switchHideCurrentSpeed.visibility =
            if (settings.useSignOverlay()) View.VISIBLE else View.GONE
        switchSign.setOnCheckedChangeListener { _, isChecked ->
            settings.setUseSignOverlay(isChecked)
        }

        switchHideCurrentSpeed.isChecked = settings.hideCurrentSpeed()
        switchHideCurrentSpeed.setOnCheckedChangeListener { _, isChecked ->
            settings.setHideCurrentSpeed(isChecked)
        }

        // -----------------------------
        // TRANSPARENCY
        // -----------------------------
        seekBar = findViewById(R.id.transparencySeekBar)
        preview = findViewById(R.id.overlayPreview)

        val alpha = prefs.getInt("overlay_alpha", 200)
        seekBar.progress = alpha
        updatePreview(alpha)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                updatePreview(progress)
                prefs.edit().putInt("overlay_alpha", progress).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })


        // ---------------------------------------------------------
        // AUTO START + MINIMIZE
        // ---------------------------------------------------------
        swAutoStart.isChecked = prefsAuto.getBoolean("auto_start_service", false)
        swMinimizeOnStart.isChecked = settings.getMinimizeOnStart()

        swMinimizeOnStart.visibility =
            if (swAutoStart.isChecked) View.VISIBLE else View.GONE

        swAutoStart.setOnCheckedChangeListener { _, isChecked ->
            prefsAuto.edit().putBoolean("auto_start_service", isChecked).apply()
            swMinimizeOnStart.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        swMinimizeOnStart.setOnCheckedChangeListener { _, checked ->
            settings.setMinimizeOnStart(checked)
        }

        // ---------------------------------------------------------
        // BROADCAST
        // ---------------------------------------------------------
        swBroadcast.isChecked = settings.isBroadcastEnabled()
        swBroadcast.setOnCheckedChangeListener { _, checked ->
            settings.setBroadcastEnabled(checked)
        }

        // ---------------------------------------------------------
        // KMH / MPH
        // ---------------------------------------------------------
        swUseMph.isChecked = settings.usesMph()
        swUseMph.setOnCheckedChangeListener { _, checked ->
            settings.setUseMph(checked)
        }

        // ---------------------------------------------------------
        // OVERSPEED MODE
        // ---------------------------------------------------------
        val isPctMode = settings.isOverspeedModePercentage()
        swOverspeedMode.isChecked = isPctMode
        updateOverspeedUI(isPctMode)

        swOverspeedMode.setOnCheckedChangeListener { _, checked ->
            settings.setOverspeedModePercentage(checked)
            updateOverspeedUI(checked)
        }

        seekOverspeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                updateOverspeedLabel(value)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                val value = seekOverspeed.progress

                if (settings.isOverspeedModePercentage()) {
                    settings.setOverspeedPercentage(value)
                    /*Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.overspeed_saved_percent, value),
                        Toast.LENGTH_SHORT
                    ).show() */
                    Handler(Looper.getMainLooper()).postDelayed({
                        ToastUtils.show(
                            this@SettingsActivity,
                            prefs,
                            getString(R.string.overspeed_saved_percent, value)
                        )
                    }, 80)
                } else {
                    settings.setOverspeedFixedKmh(value)
                    /*Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.overspeed_saved_fixed, value),
                        Toast.LENGTH_SHORT
                    ).show() */
                    Handler(Looper.getMainLooper()).postDelayed({
                        ToastUtils.show(
                            this@SettingsActivity,
                            prefs,
                            getString(R.string.overspeed_saved_fixed, value)
                        )
                    }, 80)
                }
            }
        })

        // ---------------------------------------------------------
        // BEEP VOLUME
        // ---------------------------------------------------------
        val savedVol = settings.getBeepVolume()
        seekBeepVolume.progress = (savedVol * 100).toInt()
        txtBeepVolumeLabel.text = getString(R.string.beep_volume_label, (savedVol * 100).toInt())

        seekBeepVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val vol = progress / 100f
                settings.setBeepVolume(vol)
                txtBeepVolumeLabel.text = getString(R.string.beep_volume_label, progress)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // ---------------------------------------------------------
        // TEST BEEP
        // ---------------------------------------------------------
        // Below should ALWAYS work but not on FYT units with the builtin
        // FM app and builtin Media player
        // USAGE_ALARM doesn't work either on FYTs as FYT blocks it
        // Use navigation settings to not be blocked by FYT FM/Media app

        var testTripleBeep: AudioTrack? = null

        btnTestBeep.setOnClickListener {
            if (testTripleBeep == null) {
                val gen = ToneGenerator()
                val tone = gen.generateTone(1870.0, 160)
                val gap  = gen.generateSilence(25)

                testTripleBeep = gen.buildTrack(
                    tone, gap,
                    tone, gap,
                    tone
                )
            }

            val vol = settings.getBeepVolume()
            testTripleBeep?.setStereoVolume(vol, vol)
            testTripleBeep?.stop()
            testTripleBeep?.reloadStaticData()
            testTripleBeep?.play()

            // Only to test
            /*ToastUtils.show(
                this@SettingsActivity,
                prefs,
                "You tested the beep"
            )*/
        }

        

        // ---------------------------------------------------------
        // BRIGHTNESS
        // ---------------------------------------------------------
        seekBrightness.progress = prefs.getInt("speedo_brightness", 100)
        seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                prefs.edit().putInt("speedo_brightness", value).apply()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // ---------------------------------------------------------
        // SPEEDO SIZE
        // ---------------------------------------------------------
        val savedScale = prefs.getFloat("overlay_text_scale", 1.0f)
        val index = sizeScales.indexOfFirst { it == savedScale }.let { if (it == -1) 2 else it }

        seekSpeedoSize.progress = index
        txtSpeedoSizeValue.text = sizeLabels[index]

        seekSpeedoSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                txtSpeedoSizeValue.text = sizeLabels[value]
                prefs.edit().putFloat("overlay_text_scale", sizeScales[value]).apply()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })


        // ---------------------------------------------------------
        // SPEED LIMIT FETCH INTERVAL SPINNER
        // ---------------------------------------------------------
        val intervalLabels = arrayOf(
            getString(R.string.fetch_interval_2s),
            getString(R.string.fetch_interval_4s),
            getString(R.string.fetch_interval_8s)
        )

        val intervalValues = longArrayOf(2000L, 4000L, 8000L)

        val intervalAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            intervalLabels
        )
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFetchInterval.adapter = intervalAdapter

        val savedInterval = settings.getSpeedLimitFetchIntervalMs()
        val intervalIndex = intervalValues.indexOf(savedInterval).let { if (it == -1) 1 else it }
        spinnerFetchInterval.setSelection(intervalIndex)

        spinnerFetchInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                settings.setSpeedLimitFetchIntervalMs(intervalValues[pos])
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // ---------------------------------------------------------
        // MINIMUM DISTANCE SPINNER
        // ---------------------------------------------------------
        val distLabels = arrayOf(
            getString(R.string.min_distance_0m),
            "10 m",
            "25 m",
            "50 m"
        )

        val distValues = floatArrayOf(0f, 10f, 25f, 50f)

        val distAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            distLabels
        )
        distAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMinDistance.adapter = distAdapter

        val savedDist = settings.getMinDistanceForFetch()
        val distIndex = distValues.toList().indexOf(savedDist).let { if (it == -1) 1 else it }
        spinnerMinDistance.setSelection(distIndex)

        spinnerMinDistance.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                settings.setMinDistanceForFetch(distValues[pos])
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    override fun onDestroy() {
        testTripleBeep?.release()
        testTripleBeep = null

        super.onDestroy()
    }

    // ---------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------
    private fun updateOverspeedUI(isPctMode: Boolean) {
        if (isPctMode) {
            seekOverspeed.max = 30
            seekOverspeed.progress = settings.getOverspeedPercentage()
            updateOverspeedLabel(settings.getOverspeedPercentage())
            swOverspeedMode.text = getString(R.string.overspeed_mode_percentage)
        } else {
            seekOverspeed.max = 20
            seekOverspeed.progress = settings.getOverspeedFixedKmh()
            updateOverspeedLabel(settings.getOverspeedFixedKmh())
            swOverspeedMode.text = getString(R.string.overspeed_mode_fixed)
        }
    }

    private fun updateOverspeedLabel(value: Int) {
        if (settings.isOverspeedModePercentage()) {
            txtOverspeedLabel.text = getString(R.string.overspeed_label_percent, value)
        } else {
            txtOverspeedLabel.text = getString(R.string.overspeed_label_fixed, value)
        }
    }

    private fun updatePreview(alpha: Int) {
        preview.background?.alpha = alpha
    }

    private fun isNightMode(): Boolean {
        val uiMode = resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
