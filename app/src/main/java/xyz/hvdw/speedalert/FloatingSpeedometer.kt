package xyz.hvdw.speedalert

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.edit
import xyz.hvdw.speedalert.databinding.ViewSpeedometerBinding

class FloatingSpeedometer(
    private val ctx: Context,
    private val settings: SettingsManager
) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var binding: ViewSpeedometerBinding? = null

    // Save position + minimized state
    private val prefs = ctx.getSharedPreferences("speedometer_prefs", Context.MODE_PRIVATE)
    private var isMinimized = prefs.getBoolean("minimized", false)

    // Window layout params (optimized for head units)
    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.getInt("pos_x", 100)
        y = prefs.getInt("pos_y", 100)
    }

    // Theme detection
    private val isDarkMode: Boolean
        get() = (ctx.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // Colors
    private val colorNormal: Int
        get() = if (isDarkMode) Color.WHITE else Color.BLACK

    private val colorOverspeed: Int
        get() = if (isDarkMode) Color.parseColor("#FF453A") else Color.parseColor("#FF3B30")

    private val backgroundColor: Int
        get() = if (isDarkMode) Color.parseColor("#222222") else Color.parseColor("#FFFFFF")

    // Pulse animation
    private var pulseAnimator: AnimatorSet? = null
    private var isPulsing = false

    // ---------------------------------------------------------
    // SHOW / HIDE
    // ---------------------------------------------------------
    fun show() {
        if (view != null) return

        binding = ViewSpeedometerBinding.inflate(LayoutInflater.from(ctx))
        view = binding!!.root

        applyTheme()
        applyTransparency()
        applyMinimizedState()
        setupTapToggle()
        setupDrag()

        try {
            wm.addView(view, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        stopPulse()
        view?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {}
        }
        view = null
        binding = null
    }

    // ---------------------------------------------------------
    // STYLE UPDATE
    // ---------------------------------------------------------
    fun updateStyle(@Suppress("UNUSED_PARAMETER") _settings: SettingsManager) {
        applyTheme()
        applyTransparency()
    }

    private fun applyTheme() {
        val b = binding ?: return

        val bg = GradientDrawable().apply {
            cornerRadius = 32f
            setColor(backgroundColor)
            setStroke(2, if (isDarkMode) Color.DKGRAY else Color.LTGRAY)
        }

        b.speedometerRoot.background = bg
        b.speedometerRoot.elevation = 16f

        b.textCurrentSpeed.setTextColor(colorNormal)
        b.textLimit.setTextColor(colorNormal)
        b.textUnit.setTextColor(colorNormal)
    }

    private fun applyTransparency() {
        view?.alpha = if (settings.getSemiTransparent()) 0.6f else 1f
    }

    private fun applyMinimizedState() {
        val b = binding ?: return

        if (isMinimized) {
            b.textLimit.visibility = View.GONE
            b.textCurrentSpeed.textSize = 22f
        } else {
            b.textLimit.visibility = View.VISIBLE
            b.textCurrentSpeed.textSize = 32f
        }
    }

    // ---------------------------------------------------------
    // SPEED + LIMIT UPDATE
    // ---------------------------------------------------------
    fun updateSpeed(currentSpeedKmh: Int, limitKmh: Int?, isOverspeed: Boolean) {
        val b = binding ?: return

        val useMph = settings.getUseMph()

        val speed = if (useMph) (currentSpeedKmh * 0.621371).toInt() else currentSpeedKmh
        val limit = if (useMph) limitKmh?.let { (it * 0.621371).toInt() } else limitKmh

        b.textCurrentSpeed.text = speed.toString()
        b.textLimit.text = limit?.toString() ?: ctx.getString(R.string.no_speed_limit)
        b.textUnit.text = if (useMph)
            ctx.getString(R.string.unit_mph)
        else
            ctx.getString(R.string.unit_kmh)

        if (isOverspeed) {
            b.textCurrentSpeed.setTextColor(colorOverspeed)
            startPulse()
        } else {
            b.textCurrentSpeed.setTextColor(colorNormal)
            stopPulse()
        }
    }

    // ---------------------------------------------------------
    // PULSE ANIMATION
    // ---------------------------------------------------------
    private fun startPulse() {
        if (isPulsing || view == null) return

        val v = view!!

        val scaleX = ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, 1.1f).apply {
            duration = 450
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }

        val scaleY = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, 1.1f).apply {
            duration = 450
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }

        pulseAnimator = AnimatorSet().apply {
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(scaleX, scaleY)
            start()
        }

        isPulsing = true
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null

        view?.apply {
            scaleX = 1f
            scaleY = 1f
        }

        isPulsing = false
    }

    // ---------------------------------------------------------
    // TAP TO MINIMIZE
    // ---------------------------------------------------------
    private fun setupTapToggle() {
        view?.setOnClickListener {
            isMinimized = !isMinimized
            prefs.edit { putBoolean("minimized", isMinimized) }
            applyMinimizedState()
        }
    }

    // ---------------------------------------------------------
    // DRAG TO MOVE
    // ---------------------------------------------------------
    private fun setupDrag() {
        view?.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {

                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - touchX).toInt()
                        layoutParams.y = initialY + (event.rawY - touchY).toInt()

                        try {
                            view?.let { wm.updateViewLayout(it, layoutParams) }
                        } catch (_: Exception) {}

                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        prefs.edit {
                            putInt("pos_x", layoutParams.x)
                            putInt("pos_y", layoutParams.y)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }
}
