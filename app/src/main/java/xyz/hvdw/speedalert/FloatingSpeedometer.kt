package xyz.hvdw.speedalert

import android.content.Context
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

    private var windowManager: WindowManager? = null
    private var view: View? = null

    private lateinit var params: WindowManager.LayoutParams

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

        // Restore saved position (clamped to avoid off-screen)
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
                    false   // important for FYT units
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // Save final position
                    settings.setOverlayX(params.x)
                    settings.setOverlayY(params.y)
                    true
                }

                else -> false
            }
        }
    }

    fun updateSpeed(speed: Int, limit: Int, overspeed: Boolean) {
        val useMph = settings.getUseMph()

        val displaySpeed = if (useMph) (speed * 0.621371).toInt() else speed
        val displayLimit = if (useMph && limit > 0) (limit * 0.621371).toInt() else limit

        val unit = if (useMph)
            context.getString(R.string.unit_mph)
        else
            context.getString(R.string.unit_kmh)

        val limitPrefix = context.getString(R.string.overlay_limit_prefix)
        val noLimit = context.getString(R.string.overlay_no_limit)

        txtSpeed?.text = "$displaySpeed $unit"
        txtLimit?.text = if (limit > 0) "$limitPrefix $displayLimit $unit" else noLimit

        if (overspeed) {
            txtSpeed?.setTextColor(0xFFFF4444.toInt()) // red
            txtLimit?.setTextColor(0xFFFF4444.toInt()) // red
        } else {
            txtSpeed?.setTextColor(0xFFFFFFFF.toInt()) // white
            txtLimit?.setTextColor(0xFFFFFFFF.toInt()) // white
        }
    }

    fun showNoGps() {
        val unit = if (settings.getUseMph())
            context.getString(R.string.unit_mph)
        else
            context.getString(R.string.unit_kmh)

        val noGps = context.getString(R.string.overlay_no_gps)

        txtSpeed?.text = "-- $unit"
        txtLimit?.text = noGps

        txtSpeed?.setTextColor(0xFFFFAA00.toInt()) // orange
        txtLimit?.setTextColor(0xFFFFAA00.toInt())
    }
}
