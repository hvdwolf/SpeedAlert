package xyz.hvdw.speedalert

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class FloatingToastOverlay(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var toastView: View? = null

    fun show(message: String, meters: Int) {
        hide() // remove previous toast if still visible

        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val scale = prefs.getFloat("overlay_text_scale", 1.0f)
        val baseTextSize = 16f

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(context)
        toastView = inflater.inflate(R.layout.overlay_toast, null)

        val txt = toastView!!.findViewById<TextView>(R.id.txtToastMessage)
        txt.text = message

        txt.textSize = baseTextSize * scale

        if (meters < 100) {
            txt.setTextColor(Color.RED)
        } else {
            txt.setTextColor(Color.WHITE)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 150  // distance from top

        windowManager?.addView(toastView, params)

        // Auto-remove after 2 seconds
        toastView?.postDelayed({ hide() }, 2000)
    }

    fun hide() {
        toastView?.let {
            windowManager?.removeView(it)
        }
        toastView = null
    }
}
