package xyz.hvdw.speedalert

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class FloatingSpeedometer(
    private val context: Context,
    private val settings: SettingsManager
) {

    private var windowManager: WindowManager? = null
    private var view: View? = null

    private var txtSpeed: TextView? = null
    private var txtLimit: TextView? = null

    fun show() {
        if (view != null) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(context)
        view = inflater.inflate(R.layout.overlay_speedometer, null)

        txtSpeed = view!!.findViewById(R.id.txtOverlaySpeed)
        txtLimit = view!!.findViewById(R.id.txtOverlayLimit)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = settings.getOverlayX()
        params.y = settings.getOverlayY()

        windowManager?.addView(view, params)
    }

    fun hide() {
        if (view != null) {
            windowManager?.removeView(view)
            view = null
        }
    }

    fun updateSpeed(speed: Int, limit: Int, overspeed: Boolean) {
        txtSpeed?.text = speed.toString()
        txtLimit?.text = if (limit > 0) "Limit: $limit" else "-"

        if (overspeed) {
            txtSpeed?.setTextColor(0xFFFF4444.toInt()) // red
            txtLimit?.setTextColor(0xFFFF4444.toInt()) // red
        } else {
            txtSpeed?.setTextColor(0xFFFFFFFF.toInt()) // white
            txtLimit?.setTextColor(0xFFFFFFFF.toInt()) // white
        }
    }

    fun showNoGps() {
        txtSpeed?.text = "--"
        txtLimit?.text = "No GPS"
        txtSpeed?.setTextColor(0xFFFFAA00.toInt()) // orange
        txtLimit?.setTextColor(0xFFFFAA00.toInt())
    }

}
