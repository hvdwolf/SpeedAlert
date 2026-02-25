package xyz.hvdw.speedalert

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
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

    private lateinit var spinnerFetchInterval: Spinner
    private lateinit var spinnerMinDistance: Spinner

    private lateinit var swAutoStart: Switch
    private lateinit var swMinimizeOnStart: Switch

    private lateinit var settings: SettingsManager

    private val prefs by lazy {
        getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

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

    private var testSoundPool: SoundPool? = null
    private var testBeepId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = SettingsManager(this)

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

        spinnerFetchInterval = findViewById(R.id.spinnerFetchInterval)
        spinnerMinDistance = findViewById(R.id.spinnerMinDistance)

        swAutoStart = findViewById(R.id.switchAutoStart)
        swMinimizeOnStart = findViewById(R.id.switchMinimizeOnStart)

        findViewById<Button>(R.id.btnMphInfo).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.mph_info_title))
                .setMessage(getString(R.string.mph_info_message))
                .setPositiveButton("OK", null)
                .show()
        }

        // ---------------------------------------------------------
        // SHOW OVERLAY
        // ---------------------------------------------------------
        swShowOverlay.isChecked = settings.getShowSpeedometer()
        swShowOverlay.setOnCheckedChangeListener { _, checked ->
            settings.setShowSpeedometer(checked)
        }

        switchSign.isChecked = settings.useSignOverlay()
        switchSign.setOnCheckedChangeListener { _, isChecked ->
            settings.setUseSignOverlay(isChecked)
        }

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
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.overspeed_saved_percent, value),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    settings.setOverspeedFixedKmh(value)
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.overspeed_saved_fixed, value),
                        Toast.LENGTH_SHORT
                    ).show()
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
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        testSoundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        testBeepId = testSoundPool!!.load(this, R.raw.beep, 1)

        btnTestBeep.setOnClickListener {
            val vol = settings.getBeepVolume()
            testSoundPool?.play(testBeepId, vol, vol, 1, 0, 1f)
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
        testSoundPool?.release()
        testSoundPool = null
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
}
