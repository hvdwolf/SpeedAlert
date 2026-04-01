package xyz.hvdw.speedalert

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class ToneGenerator(
    private val sampleRate: Int = 44100
) {

    fun generateTone(
        freqHz: Double,
        durationMs: Int
    ): ShortArray {
        val samples = sampleRate * durationMs / 1000
        val buffer = ShortArray(samples)

        for (i in 0 until samples) {
            val angle = 2.0 * Math.PI * i * freqHz / sampleRate
            buffer[i] = (Math.sin(angle) * Short.MAX_VALUE).toInt().toShort()
        }

        return buffer
    }

    fun generateSilence(durationMs: Int): ShortArray {
        val samples = sampleRate * durationMs / 1000
        return ShortArray(samples) { 0 }
    }

    fun buildTrack(vararg chunks: ShortArray): AudioTrack {
        val totalSamples = chunks.sumOf { it.size }
        val buffer = ShortArray(totalSamples)

        var index = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, buffer, index, chunk.size)
            index += chunk.size
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = AudioTrack(
            attributes,
            format,
            buffer.size * 2,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        track.write(buffer, 0, buffer.size)
        return track
    }
}
