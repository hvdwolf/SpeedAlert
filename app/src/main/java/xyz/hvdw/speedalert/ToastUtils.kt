package xyz.hvdw.speedalert

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

object ToastUtils {

    fun show(context: Context, prefs: SharedPreferences, message: String) {
        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.toast_custom, null)

        val root = layout.findViewById<LinearLayout>(R.id.toastRoot)
        val text = layout.findViewById<TextView>(R.id.toastText)

        text.text = message

        // Use your existing prefs-based scale
        val scale = prefs.getFloat("overlay_text_scale", 1.0f)
        text.textSize = 18f * scale

        // Day/night theming
        val isNight = isNightMode(context)

        val bgColor = if (isNight) 0xCC222222.toInt() else 0xCC000000.toInt()
        val textColor = 0xFFFFFFFF.toInt()

        val bg = ContextCompat.getDrawable(context, R.drawable.toast_bg)
        bg?.setTint(bgColor)
        root.background = bg

        text.setTextColor(textColor)

        val toast = Toast(context)
        toast.duration = Toast.LENGTH_SHORT
        toast.view = layout
        toast.show()
    }

    private fun isNightMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
