package xyz.hvdw.speedalert

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.media.AudioTrack
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.animation.RotateAnimation
import android.widget.*
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File

private const val KEY_CURRENT_SPEED_MODE = "current_speed_display_mode"


class SettingsActivity : AppCompatActivity() {

    // Overlay
    private lateinit var swShowOverlay: Switch
    private lateinit var switchSign: Switch
    private lateinit var spinnerCurrentSpeedLabel: TextView
    private lateinit var spinnerCurrentSpeed: Spinner
    private lateinit var swShowUnits: Switch
    private lateinit var seekBrightness: SeekBar
    private lateinit var seekSpeedoSize: SeekBar
    private lateinit var txtSpeedoSizeValue: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var preview: FrameLayout

    // Speed & Units
    private lateinit var swUseMph: Switch

    // Overspeed
    private lateinit var swOverspeedMode: Switch
    private lateinit var seekOverspeed: SeekBar
    private lateinit var txtOverspeedLabel: TextView
    private lateinit var swUseCountryFallback: Switch

    // Audio
    private lateinit var seekBeepVolume: SeekBar
    private lateinit var txtBeepVolumeLabel: TextView
    private lateinit var btnTestBeep: Button
    private lateinit var swShowMuteButton: Switch
    private lateinit var swBeepOnce: Switch
    private lateinit var ttsManager: TTSManager

    // Fetching
    private lateinit var spinnerFetchInterval: Spinner
    private lateinit var spinnerMinDistance: Spinner

    // Advanced
    private lateinit var swBroadcast: Switch
    private lateinit var swAutoStart: Switch
    private lateinit var swMinimizeOnStart: Switch

    // Settings
    private lateinit var prefs: SharedPreferences
    private lateinit var settings: SettingsManager

    private val prefsAuto by lazy {
        getSharedPreferences("speedalert_prefs", MODE_PRIVATE)
    }


    private val sizeLabels by lazy {
        arrayOf(
            "50%",
            "75%",
            "100%",
            "125%",
            "150%",
            "175%",
            "200%",
            "225%",
            "250%",
            "275%",
            "300%",
        )
    }


    private val sizeScales = floatArrayOf(0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 1.75f, 2.00f, 2.25f, 2.50f, 2.75f, 3.00f)

    private var testTripleBeep: AudioTrack? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = SettingsManager(this)
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        // ---------------------------------------------------------
        // EXPANDABLE SECTIONS + ARROWS
        // ---------------------------------------------------------
        setupExpandableSection(
            header = findViewById(R.id.headerOverlay),
            section = findViewById(R.id.sectionOverlay),
            arrow = findViewById(R.id.arrowOverlay)
        )

        setupExpandableSection(
            header = findViewById(R.id.headerSpeedUnits),
            section = findViewById(R.id.sectionSpeedUnits),
            arrow = findViewById(R.id.arrowSpeedUnits)
        )

        setupExpandableSection(
            header = findViewById(R.id.headerOverspeed),
            section = findViewById(R.id.sectionOverspeed),
            arrow = findViewById(R.id.arrowOverspeed)
        )

        setupExpandableSection(
            header = findViewById(R.id.headerAudio),
            section = findViewById(R.id.sectionAudio),
            arrow = findViewById(R.id.arrowAudio)
        )

        setupExpandableSection(
            header = findViewById(R.id.headerFetching),
            section = findViewById(R.id.sectionFetching),
            arrow = findViewById(R.id.arrowFetching)
        )

        setupExpandableSection(
            header = findViewById(R.id.headerAdvanced),
            section = findViewById(R.id.sectionAdvanced),
            arrow = findViewById(R.id.arrowAdvanced)
        )

        // ---------------------------------------------------------
        // FIND VIEWS
        // ---------------------------------------------------------
        swShowOverlay = findViewById(R.id.swShowOverlay)
        switchSign = findViewById(R.id.switchSignOverlay)
        //switchHideCurrentSpeed = findViewById(R.id.switchHideCurrentSpeed)
        spinnerCurrentSpeedLabel = findViewById(R.id.spinnerCurrentSpeedLabel)
        spinnerCurrentSpeed = findViewById(R.id.spinnerCurrentSpeed)
        swShowUnits = findViewById(R.id.swShowUnits)
        seekBrightness = findViewById(R.id.seekBrightness)
        seekSpeedoSize = findViewById(R.id.seekSpeedoSize)
        txtSpeedoSizeValue = findViewById(R.id.txtSpeedoSizeValue)
        seekBar = findViewById(R.id.transparencySeekBar)
        preview = findViewById(R.id.overlayPreview)

        swUseMph = findViewById(R.id.swUseMph)

        swOverspeedMode = findViewById(R.id.swOverspeedMode)
        seekOverspeed = findViewById(R.id.seekOverspeed)
        txtOverspeedLabel = findViewById(R.id.txtOverspeedLabel)
        swUseCountryFallback = findViewById(R.id.swUseCountryFallback)

        seekBeepVolume = findViewById(R.id.seekBeepVolume)
        txtBeepVolumeLabel = findViewById(R.id.txtBeepVolumeLabel)
        btnTestBeep = findViewById(R.id.btnTestBeep)
        swShowMuteButton = findViewById(R.id.swShowMuteButton)
        swBeepOnce = findViewById(R.id.swBeepOnce)
        swBeepOnce.isChecked = settings.beepOnce()

        ttsManager = TTSManager(this, settings)

        swBeepOnce.setOnCheckedChangeListener { _, checked ->
            settings.setBeepOnce(checked)
        }

        spinnerFetchInterval = findViewById(R.id.spinnerFetchInterval)
        spinnerMinDistance = findViewById(R.id.spinnerMinDistance)

        findViewById<TextView>(R.id.tvDownloadInstallDb).setOnClickListener {
            startActivity(Intent(this, DatabaseBrowserActivity::class.java))
        }

        swBroadcast = findViewById(R.id.swBroadcast)
        swAutoStart = findViewById(R.id.switchAutoStart)
        swMinimizeOnStart = findViewById(R.id.switchMinimizeOnStart)

        // ---------------------------------------------------------
        // OVERLAY
        // ---------------------------------------------------------
        swShowOverlay.isChecked = settings.getShowSpeedometer()
        swShowOverlay.setOnCheckedChangeListener { _, checked ->
            settings.setShowSpeedometer(checked)
        }

        switchSign.isChecked = settings.useSignOverlay()
        switchSign.setOnCheckedChangeListener { _, checked ->
            settings.setUseSignOverlay(checked)
        }

        val currentSpeedLabels = arrayOf(
                getString(R.string.show_current_speed),
                getString(R.string.show_current_speed_when_overspeeding),
                getString(R.string.hide_current_speed),
        )
        val currentSpeedAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, currentSpeedLabels
        )
        currentSpeedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCurrentSpeed.adapter = currentSpeedAdapter

        // Load saved value using settings helper
        val savedMode = settings.getInt(KEY_CURRENT_SPEED_MODE, 0)
        spinnerCurrentSpeed.setSelection(savedMode)

        // Save when changed
        spinnerCurrentSpeed.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                settings.set(KEY_CURRENT_SPEED_MODE, position)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        swShowUnits.isChecked = settings.showUnits()
            swShowUnits.setOnCheckedChangeListener { _, checked ->
                settings.setShowUnits(checked)
            }

        // Transparency
        val alpha = prefs.getInt("overlay_alpha", 200)
        seekBar.progress = alpha
        updatePreview(alpha)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                updatePreview(value)
                prefs.edit().putInt("overlay_alpha", value).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Brightness
        seekBrightness.progress = prefs.getInt("speedo_brightness", 100)
        seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                prefs.edit().putInt("speedo_brightness", value).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Speedo size
        val savedScaleRaw = prefs.getFloat("overlay_text_scale", 1.0f)
        val savedScale = sizeScales.minByOrNull { kotlin.math.abs(it - savedScaleRaw) } ?: 1.0f
        val index = sizeScales.indexOfFirst { it == savedScale }.let { if (it == -1) 2 else it }
        seekSpeedoSize.progress = index
        txtSpeedoSizeValue.text = sizeLabels[index]

        // Position floating label correctly on startup
        seekSpeedoSize.post {
            val progress = seekSpeedoSize.progress
            val scale = sizeScales[progress]
            txtSpeedoSizeValue.text = "${(scale * 100).toInt()}%"

            val thumb = seekSpeedoSize.thumb
            val thumbCenter = thumb.bounds.centerX().toFloat()
            val barCenter = seekSpeedoSize.width / 2f

            var tx = thumbCenter - barCenter

            // Clamp to prevent clipping
            val minX = - (txtSpeedoSizeValue.width / 2f)
            val maxX = seekSpeedoSize.width - (txtSpeedoSizeValue.width / 2f)
            tx = tx.coerceIn(minX, maxX)

            txtSpeedoSizeValue.translationX = tx
        }

        seekSpeedoSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                val scale = sizeScales[value]
                val pct = (scale * 100).toInt()
                txtSpeedoSizeValue.text = "$pct%"

                sb?.let {
                    val thumb = it.thumb

                    val thumbCenter = thumb.bounds.centerX().toFloat()
                    val barCenter = seekSpeedoSize.width / 2f

                    // Desired translation
                    var tx = thumbCenter - barCenter

                    // Clamp so the label never leaves the FrameLayout
                    val minX = - (txtSpeedoSizeValue.width / 2f)
                    val maxX = seekSpeedoSize.width - (txtSpeedoSizeValue.width / 2f)

                    tx = tx.coerceIn(minX, maxX)

                    txtSpeedoSizeValue.translationX = tx
                }

                prefs.edit().putFloat("overlay_text_scale", scale).apply()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // ---------------------------------------------------------
        // SPEED & UNITS
        // ---------------------------------------------------------
        swUseMph.isChecked = settings.usesMph()
        swUseMph.setOnCheckedChangeListener { _, checked ->
            settings.setUseMph(checked)
        }

        findViewById<Button>(R.id.btnMphInfo).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.mph_info_title))
                .setMessage(getString(R.string.mph_info_message))
                .setPositiveButton("OK", null)
                .show()
        }

        // ---------------------------------------------------------
        // OVERSPEED
        // ---------------------------------------------------------
        val isPct = settings.isOverspeedModePercentage()
        swOverspeedMode.isChecked = isPct
        updateOverspeedUI(isPct)

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
                    Handler(Looper.getMainLooper()).postDelayed({
                        ToastUtils.show(
                            this@SettingsActivity,
                            prefs,
                            getString(R.string.overspeed_saved_percent, value)
                        )
                    }, 80)
                } else {
                    settings.setOverspeedFixedKmh(value)
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

        swUseCountryFallback.isChecked = settings.useCountryFallback()
        swUseCountryFallback.setOnCheckedChangeListener { _, checked ->
            settings.setUseCountryFallback(checked)
        }

        // ---------------------------------------------------------
        // AUDIO
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

        btnTestBeep.setOnClickListener {
            if (testTripleBeep == null) {
                val gen = ToneGenerator()
                val attrs = settings.getAudioAttributes()
                val tone = gen.generateTone(1870.0, 160)
                val gap = gen.generateSilence(25)
                testTripleBeep = gen.buildTrack(attrs, tone, gap, tone, gap, tone)
            }

            val vol = settings.getBeepVolume()
            testTripleBeep?.setStereoVolume(vol, vol)
            testTripleBeep?.stop()
            testTripleBeep?.reloadStaticData()
            testTripleBeep?.play()
        }

        swShowMuteButton.isChecked = settings.showSpeakerMuteButton()
        swShowMuteButton.setOnCheckedChangeListener { _, checked ->
            settings.setShowSpeakerMuteButton(checked)
            if (!checked) settings.setMuted(false)
        }

        // TTS switch
        val switch = findViewById<Switch>(R.id.speak_tts_speedcam_warning)
        switch.isChecked = settings.getSpeakTtsSpeedcamWarning()
        switch.setOnCheckedChangeListener { _, isChecked ->
            settings.setSpeakTtsSpeedcamWarning(isChecked)
        }

        val testButton = findViewById<Button>(R.id.test_tts_speedcam_warning)
        // TTS  testbutton
        testButton.setOnClickListener {
            if (settings.getSpeakTtsSpeedcamWarning()) {
                ttsManager.testSpeedcamWarning()
            } else {
                Toast.makeText(this, "TTS warning is disabled", Toast.LENGTH_SHORT).show()
            }
        }

        val spinner = findViewById<Spinner>(R.id.spinnerAudioOutput)

        val current = settings.getAudioStream()
        val audioIndex = when (current) {
            "media" -> 0
            "notification" -> 1
            "alarm" -> 2
            "ring" -> 3
            "system" -> 4
            "navigation" -> 5
            else -> 0
        }
        spinner.setSelection(audioIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val value = when (pos) {
                    0 -> "media"
                    1 -> "notification"
                    2 -> "alarm"
                    3 -> "ring"
                    4 -> "system"
                    5 -> "navigation"
                    else -> "media"
                }
                settings.setAudioStream(value)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }


        // ---------------------------------------------------------
        // FETCHING
        // ---------------------------------------------------------
        val intervalLabels = arrayOf(
            getString(R.string.fetch_interval_2s),
            getString(R.string.fetch_interval_4s),
            getString(R.string.fetch_interval_8s)
        )
        val intervalValues = longArrayOf(2000L, 4000L, 8000L)

        val intervalAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, intervalLabels
        )
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFetchInterval.adapter = intervalAdapter

        val savedInterval = settings.getSpeedLimitFetchIntervalMs()
        val intervalIndex = intervalValues.indexOf(savedInterval).let { if (it == -1) 1 else it }
        spinnerFetchInterval.setSelection(intervalIndex)

        spinnerFetchInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                settings.setSpeedLimitFetchIntervalMs(intervalValues[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val distLabels = arrayOf(
            getString(R.string.min_distance_0m),
            "10 m",
            "25 m",
            "50 m"
        )
        val distValues = floatArrayOf(0f, 10f, 25f, 50f)

        val distAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, distLabels
        )
        distAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMinDistance.adapter = distAdapter

        val savedDist = settings.getMinDistanceForFetch()
        //val distIndex = distValues.indexOf(savedDist).let { if (it == -1) 1 else it }
        val distIndex = distValues.toList().indexOf(savedDist).let { if (it == -1) 1 else it }
        spinnerMinDistance.setSelection(distIndex)

        spinnerMinDistance.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                settings.setMinDistanceForFetch(distValues[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // ---------------------------------------------------------
        // ADVANCED
        // ---------------------------------------------------------
        swBroadcast.isChecked = settings.isBroadcastEnabled()
        swBroadcast.setOnCheckedChangeListener { _, checked ->
            settings.setBroadcastEnabled(checked)
        }

        swAutoStart.isChecked = prefsAuto.getBoolean("auto_start_service", false)
        swMinimizeOnStart.isChecked = settings.getMinimizeOnStart()
        swMinimizeOnStart.visibility = if (swAutoStart.isChecked) View.VISIBLE else View.GONE

        swAutoStart.setOnCheckedChangeListener { _, checked ->
            prefsAuto.edit().putBoolean("auto_start_service", checked).apply()
            swMinimizeOnStart.visibility = if (checked) View.VISIBLE else View.GONE
        }

        swMinimizeOnStart.setOnCheckedChangeListener { _, checked ->
            settings.setMinimizeOnStart(checked)
        }
    }

    override fun onDestroy() {
        testTripleBeep?.release()
        testTripleBeep = null
        super.onDestroy()
    }

    // ---------------------------------------------------------
    // EXPAND / COLLAPSE WITH ARROW ROTATION
    // ---------------------------------------------------------
    private fun setupExpandableSection(header: View, section: View, arrow: ImageView) {
        header.setOnClickListener {
            val expanding = section.visibility != View.VISIBLE
            if (expanding) expand(section) else collapse(section)
            rotateArrow(arrow, expanding)
        }
    }

    private fun rotateArrow(arrow: ImageView, expanding: Boolean) {
        val from = if (expanding) 0f else 90f
        val to = if (expanding) 90f else 0f

        val anim = RotateAnimation(
            from, to,
            RotateAnimation.RELATIVE_TO_SELF, 0.5f,
            RotateAnimation.RELATIVE_TO_SELF, 0.5f
        )
        anim.duration = 200
        anim.fillAfter = true
        arrow.startAnimation(anim)
    }

    private fun expand(v: View) {
        v.measure(
            View.MeasureSpec.makeMeasureSpec((v.parent as View).width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val targetHeight = v.measuredHeight

        v.layoutParams.height = 0
        v.visibility = View.VISIBLE

        val anim = ValueAnimator.ofInt(0, targetHeight)
        anim.addUpdateListener {
            v.layoutParams.height = it.animatedValue as Int
            v.requestLayout()
        }
        anim.duration = 200
        anim.start()
    }

    private fun collapse(v: View) {
        val initialHeight = v.measuredHeight

        val anim = ValueAnimator.ofInt(initialHeight, 0)
        anim.addUpdateListener {
            val value = it.animatedValue as Int
            v.layoutParams.height = value
            v.requestLayout()
            if (value == 0) v.visibility = View.GONE
        }
        anim.duration = 200
        anim.start()
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
