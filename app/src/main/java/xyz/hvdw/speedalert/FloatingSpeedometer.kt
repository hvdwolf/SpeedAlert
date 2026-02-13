package xyz.hvdw.speedalert

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.max

class FloatingSpeedometer(
    private val context: Context,
    private val settings: SettingsManager
) {

    private var lastSpeed = 0
    private var lastLimit = 0
    private var lastOverspeed = false

    private var windowManager: WindowManager? = null
    private var view: View? = null

    private lateinit var params: WindowManager.LayoutParams

    private val prefs by lazy {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private var txtSpeed: TextView? = null
    private var txtLimit: TextView? = null

    // Drag helpers
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    fun show() {
        if (view != null) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(context)
        view = inflater.inflate(R.layout.overlay_speedometer, null)

        txtSpeed = view!!.findViewById(R.id.txtOverlaySpeed)
        txtLimit = view!!.findViewById(R.id.txtOverlayLimit)

        applyTextScaling()

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
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    settings.setOverlayX(params.x)
                    settings.setOverlayY(params.y)
                    true
                }

                else -> false
            }
        }
    }

    fun updateSpeed(speed: Int, limit: Int, overspeed: Boolean) {
        lastSpeed = speed
        lastLimit = limit
        lastOverspeed = overspeed

        // -----------------------------
        // UNIT LABEL (based on user preference)
        // -----------------------------
        val unit = settings.displayUnit()

        val limitPrefix = context.getString(R.string.overlay_limit_prefix)
        val noLimit = context.getString(R.string.overlay_no_limit)

        // -----------------------------
        // GPS SPEED (converted if needed)
        // -----------------------------
        val displaySpeed = settings.convertSpeed(speed)
        txtSpeed?.text = "$displaySpeed $unit"

        // -----------------------------
        // ROAD LIMIT (converted if needed)
        // -----------------------------
        val displayLimit = settings.convertSpeed(limit)

        txtLimit?.text = if (limit > 0)
            "$limitPrefix $displayLimit $unit"
        else
            noLimit

        // -----------------------------
        // COLORING
        // -----------------------------
        val normalColor = context.getColor(R.color.speed_text_day)
        val nightColor = context.getColor(R.color.speed_text_night)
        val baseColor = if (isNightMode()) nightColor else normalColor

        val brightness = prefs.getInt("speedo_brightness", 100)
        val finalColor = applyBrightness(baseColor, brightness)

        if (overspeed) {
            txtSpeed?.setTextColor(0xFFFF4444.toInt())
            txtLimit?.setTextColor(0xFFFF4444.toInt())
        } else {
            txtSpeed?.setTextColor(finalColor)
            txtLimit?.setTextColor(finalColor)
        }
    }

    fun showNoGps() {
        val unit = settings.displayUnit()
        val noGps = context.getString(R.string.overlay_no_gps)

        txtSpeed?.text = "-- $unit"
        txtLimit?.text = noGps

        txtSpeed?.setTextColor(0xFFFFAA00.toInt())
        txtLimit?.setTextColor(0xFFFFAA00.toInt())
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
        updateSpeed(lastSpeed, lastLimit, lastOverspeed)
    }

    // -----------------------------
    // TEXT SCALING
    // -----------------------------
    private fun applyTextScaling() {
        val scale = prefs.getFloat("overlay_text_scale", 1.0f)

        val baseSpeed = 28f
        val baseLimit = 18f

        txtSpeed?.textSize = baseSpeed * scale
        txtLimit?.textSize = baseLimit * scale
    }
}
