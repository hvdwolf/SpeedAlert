package xyz.hvdw.speedalert

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import android.media.AudioAttributes
import java.util.Locale

class TTSManager(
    context: Context,
    private val settings: SettingsManager
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {

            val systemLocale = Locale.getDefault()
            val result = tts?.setLanguage(systemLocale)

            ready = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

            if (!ready) {
                val fallbackResult = tts?.setLanguage(Locale.US)
                ready = fallbackResult != TextToSpeech.LANG_MISSING_DATA &&
                        fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED
            }

            pendingText?.let { speak(it) }
            pendingText = null
        }
    }

    fun speak(text: String) {
        if (!ready) {
            pendingText = text
            return
        }

        val attrs = settings.getAudioAttributes()
        tts?.setAudioAttributes(attrs)

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "tts-${System.currentTimeMillis()}"
        )
    }

    fun speak(resId: Int) {
        speak(appContext.getString(resId))
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    fun testSpeedcamWarning() {
        speak(R.string.tts_speedcam_warning)
    }

}
