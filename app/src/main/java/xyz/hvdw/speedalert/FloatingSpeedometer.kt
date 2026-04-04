package xyz.hvdw.speedalert

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max

private const val KEY_CURRENT_SPEED_MODE = "current_speed_display_mode"


class FloatingSpeedometer(
    private val context: Context,
    private val settings: SettingsManager
) {

    private var lastSpeed = 0
    private var lastLimit = 0
    private var lastOverspeed = false

    private var cameraStage: Int = 0
    private var cameraIcon: Drawable? = null
    private var imgCameraWarning: ImageView? = null

    private var windowManager: WindowManager? = null
    private var view: View? = null
    private var imgMuteState: ImageView? = null

    private lateinit var params: WindowManager.LayoutParams

    private val prefs by lazy {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    // Text mode views
    private var txtSpeed: TextView? = null
    private var txtLimit: TextView? = null

    // Sign mode views
    private var imgLimitSign: ImageView? = null
    private var txtLimitSign: TextView? = null
    private var txtSpeedSign: TextView? = null

    private var root: LinearLayout? = null
    private var signContainer: FrameLayout? = null

    // Drag helpers
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    

    fun show() {
        if (view != null) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(context)

        // Inflate correct layout based on setting
        view = if (settings.useSignOverlay()) {
            inflater.inflate(R.layout.overlay_speed_sign, null).also { v ->
                signContainer = v.findViewById(R.id.signContainer)
                imgLimitSign = v.findViewById(R.id.imgLimitSign)
                txtLimitSign = v.findViewById(R.id.txtLimitSign)
                txtSpeedSign = v.findViewById(R.id.txtSpeedSign)
                root = v.findViewById<LinearLayout>(R.id.speedometerSignRoot)
                imgMuteState = v.findViewById(R.id.imgMuteState)
                imgCameraWarning = view?.findViewById(R.id.imgCameraWarning)
            }
        } else {
            inflater.inflate(R.layout.overlay_speedometer, null).also { v ->
                txtSpeed = v.findViewById(R.id.txtOverlaySpeed)
                txtLimit = v.findViewById(R.id.txtOverlayLimit)
                root = v.findViewById<LinearLayout>(R.id.speedometerRoot)
                imgMuteState = v.findViewById(R.id.imgMuteState)
                imgCameraWarning = view?.findViewById(R.id.imgCameraWarning)
            }
        }
        // Hide or show mute icon based on settings
        if (settings.showSpeakerMuteButton()) {
            imgMuteState?.visibility = View.VISIBLE
        } else {
            imgMuteState?.visibility = View.GONE
        }


        view?.isClickable = true
        view?.isFocusable = true

        view?.setOnClickListener {
            if (!settings.showSpeakerMuteButton()) return@setOnClickListener

            val newState = !settings.isMuted()
            settings.setMuted(newState)

            Toast.makeText(
                context,
                if (newState)
                    context.getString(R.string.beep_muted)
                else
                    context.getString(R.string.beep_unmuted),
                Toast.LENGTH_SHORT
            ).show()

            updateMuteIcon()
        }


        applyTextScaling()
        applyOverlayBackgroundAlpha()

        // Apply initial mute icon state
        updateMuteIcon()

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = max(0, settings.getOverlayX())
        params.y = max(0, settings.getOverlayY())

        addDragSupport(view!!)
        windowManager?.addView(view, params)
    }

    fun hide() {
        if (view != null) {
            windowManager?.removeView(view)
            view = null
        }
    }


    private fun addDragSupport(v: View) {
        val touchSlop = 10 * context.resources.displayMetrics.density // small movement threshold

        v.setOnTouchListener { _, event ->
            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // If movement is significant → drag
                    if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(view, params)
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // If movement was tiny → treat as tap
                    if (kotlin.math.abs(dx) <= touchSlop && kotlin.math.abs(dy) <= touchSlop) {
                        v.performClick()   // ← THIS MAKES YOUR CLICK LISTENER FIRE
                    } else {
                        // Drag finished → save position
                        settings.setOverlayX(params.x)
                        settings.setOverlayY(params.y)
                    }
                    true
                }

                else -> false
            }
        }
    }


    // ---------------------------------------------------------
    // TEXT MODE
    // ---------------------------------------------------------
    fun updateSpeedTextMode(speed: Int, limit: Int, overspeed: Boolean) {
        lastSpeed = speed
        lastLimit = limit
        lastOverspeed = overspeed

        val unit = settings.displayUnit()
        val limitPrefix = context.getString(R.string.overlay_limit_prefix)
        val noLimit = context.getString(R.string.overlay_no_limit)

        val displaySpeed = settings.convertSpeed(speed)
        txtSpeed?.text = "$displaySpeed $unit"

        val displayLimit = settings.convertSpeed(limit)
        txtLimit?.text = if (limit > 0)
            "$limitPrefix $displayLimit $unit"
        else
            noLimit

        val normalColor = context.getColor(R.color.speed_text_day)
        val nightColor = context.getColor(R.color.speed_text_night)
        val baseColor = if (isNightMode()) nightColor else normalColor

        val brightness = prefs.getInt("speedo_brightness", 100)
        val finalColor = applyBrightness(baseColor, brightness)

        // Show or hide current speed
        val mode = settings.getInt(KEY_CURRENT_SPEED_MODE, 0)

        // Determine visibility based on mode + overspeed
        when (mode) {
            0 -> { // Always show
                txtSpeed?.visibility = View.VISIBLE
                txtSpeedSign?.text = "$displaySpeed $unit"
            }

            1 -> { // Only show when overspeeding
                if (overspeed) {
                    txtSpeed?.visibility = View.VISIBLE
                    txtSpeedSign?.text = "$displaySpeed $unit"
                } else {
                    txtSpeed?.visibility = View.GONE
                }
            }

            2 -> { // Never show
                txtSpeed?.visibility = View.GONE
            }
        }

        if (overspeed) {
            txtSpeed?.setTextColor(0xFFFF4444.toInt())
            txtLimit?.setTextColor(0xFFFF4444.toInt())
        } else {
            txtSpeed?.setTextColor(finalColor)
            txtLimit?.setTextColor(finalColor)
        }


        updateMuteIcon()
        applyOverlayBackgroundAlpha()
    }

    // ---------------------------------------------------------
    // SIGN MODE
    // ---------------------------------------------------------
    fun updateSpeedSignMode(speed: Int, limit: Int, overspeed: Boolean) {
        lastSpeed = speed
        lastLimit = limit
        lastOverspeed = overspeed

        val unit = settings.displayUnit()
        val displaySpeed = settings.convertSpeed(speed)

        // Always use the same background
        root?.setBackgroundResource(R.drawable.speedometer_bg)

        // Set Padding
        val pad = (8 * context.resources.displayMetrics.density).toInt()
        root?.setPadding(pad, pad, pad, pad)


        // Show or hide current speed
        val mode = settings.getInt(KEY_CURRENT_SPEED_MODE, 0)

        // Determine visibility based on mode + overspeed
        when (mode) {
            0 -> { // Always show
                txtSpeed?.visibility = View.VISIBLE
                txtSpeedSign?.text = "$displaySpeed $unit"
            }

            1 -> { // Only show when overspeeding
                if (overspeed) {
                    txtSpeed?.visibility = View.VISIBLE
                    txtSpeedSign?.text = "$displaySpeed $unit"
                } else {
                    txtSpeed?.visibility = View.GONE
                }
            }

            2 -> { // Never show
                txtSpeed?.visibility = View.GONE
            }
        }

        
        val displayLimit = settings.convertSpeed(limit)

        if (limit > 0) {
            imgLimitSign?.setImageResource(R.drawable.speed_sign_background)
            txtLimitSign?.text = displayLimit.toString()
        } else {
            imgLimitSign?.setImageResource(R.drawable.speed_sign_empty)
            txtLimitSign?.text = ""
        }

        val normalColor = context.getColor(R.color.speed_text_day)
        val nightColor = context.getColor(R.color.speed_text_night)
        val baseColor = if (isNightMode()) nightColor else normalColor

        val brightness = prefs.getInt("speedo_brightness", 100)
        val finalColor = applyBrightness(baseColor, brightness)

        if (overspeed) {
            txtSpeedSign?.setTextColor(0xFFFF4444.toInt())
            txtLimitSign?.setTextColor(0xFFCC0000.toInt())
        } else {
            txtSpeedSign?.setTextColor(finalColor)
            txtLimitSign?.setTextColor(Color.BLACK)
        }

        updateMuteIcon()
        applyOverlayBackgroundAlpha()
    }

    fun showNoGps() {
        val unit = settings.displayUnit()
        val noGps = context.getString(R.string.overlay_no_gps)

        if (settings.useSignOverlay()) {

            // LIMIT SIGN
            txtLimitSign?.text = ""
            imgLimitSign?.setImageResource(R.drawable.speed_sign_empty)
            txtLimitSign?.setTextColor(0xFFFFAA00.toInt())

            // CURRENT SPEED (may be hidden)
            if (settings.hideCurrentSpeed()) {
                txtSpeedSign?.visibility = View.GONE
            } else {
                txtSpeedSign?.visibility = View.VISIBLE
                txtSpeedSign?.text = "-- $unit"
                txtSpeedSign?.setTextColor(0xFFFFAA00.toInt())
            }

        } else {
            // TEXT MODE (unchanged)
            txtSpeed?.text = "-- $unit"
            txtLimit?.text = noGps

            txtSpeed?.setTextColor(0xFFFFAA00.toInt())
            txtLimit?.setTextColor(0xFFFFAA00.toInt())
        }

        updateMuteIcon()
    }

    private fun isNightMode(): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyBrightness(baseColor: Int, brightness: Int): Int {
        val alpha = (brightness / 100f)
        val r = (Color.red(baseColor) * alpha).toInt()
        val g = (Color.green(baseColor) * alpha).toInt()
        val b = (Color.blue(baseColor) * alpha).toInt()
        return Color.rgb(r, g, b)
    }

    fun updateBrightness() {
        if (settings.useSignOverlay()) {
            updateSpeedSignMode(lastSpeed, lastLimit, lastOverspeed)
        } else {
            updateSpeedTextMode(lastSpeed, lastLimit, lastOverspeed)
        }
    }


    private fun applyOverlayBackgroundAlpha() {
        val alpha = prefs.getInt("overlay_alpha", 200) // 0–255
        root?.background?.alpha = alpha
    }


    // Scales text and road sign
    private fun applyTextScaling() {
        val scale = prefs.getFloat("overlay_text_scale", 1.0f).toFloat()

        val baseSpeed = 28f
        val baseLimit = 18f


        if (settings.useSignOverlay()) {
            // Base size of the sign in dp
            val baseSignSizeDp = 48f
            val scaledSizePx = (baseSignSizeDp * scale * context.resources.displayMetrics.density).toInt()
            signContainer?.layoutParams?.width = scaledSizePx
            signContainer?.layoutParams?.height = scaledSizePx
            signContainer?.requestLayout()
        }


        if (!settings.hideCurrentSpeed()) {
            txtSpeedSign?.textSize = baseSpeed * scale
        }
        txtLimit?.textSize = baseLimit * scale

        txtSpeedSign?.textSize = baseSpeed * scale
        txtLimitSign?.textSize = baseLimit * scale
    }

    private fun updateMuteIcon() {
        if (!settings.showSpeakerMuteButton()) {
            imgMuteState?.visibility = View.GONE
            return
        }

        imgMuteState?.visibility = View.VISIBLE

        if (settings.isMuted()) {
            imgMuteState?.setImageResource(R.drawable.ic_volume_off)
        } else {
            imgMuteState?.setImageResource(R.drawable.ic_volume_on)
        }
    }

    fun updateCameraStage(stage: Int) {
        cameraStage = stage

        val iconRes = when (stage) {
            1 -> R.drawable.ic_camera_yellow
            2 -> R.drawable.ic_camera_orange
            3 -> R.drawable.ic_camera_red
            else -> null
        }

        if (iconRes == null) {
            imgCameraWarning?.visibility = View.GONE
            return
        }

        imgCameraWarning?.setImageResource(iconRes)
        imgCameraWarning?.visibility = View.VISIBLE
    }

}
