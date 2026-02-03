package xyz.hvdw.speedalert

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState

class MediaBroadcastManager(private val ctx: Context) {

    private var session: MediaSession? = null

    /**
     * Start a metadata-only MediaSession.
     * No audio, no playback controls, no interference with other apps.
     */
    fun start() {
        if (session != null) return

        session = MediaSession(ctx, "SpeedAlertSession").apply {

            // We expose a "neutral" playback state so the session is valid
            val state = PlaybackState.Builder()
                .setState(PlaybackState.STATE_NONE, 0L, 0f)
                .build()

            setPlaybackState(state)

            // Activate the session so metadata becomes visible to the system
            isActive = true
        }
    }

    /**
     * Stop and release the MediaSession.
     */
    fun stop() {
        session?.isActive = false
        session?.release()
        session = null
    }

    /**
     * Update metadata shown in the system UI:
     * - Album  → App name
     * - Title  → Current speed + unit
     * - Artist → Speed limit or "No limit"
     */
    fun updateMetadata(appName: String, speed: Int, limit: Int?, useMph: Boolean) {
        val s = session ?: return

        if (session?.isActive != true) start()

        // Unit from resources
        val unit = if (useMph)
            ctx.getString(R.string.unit_mph)
        else
            ctx.getString(R.string.unit_kmh)

        // Limit text from resources
        val limitText = limit?.let {
            ctx.getString(R.string.media_limit_prefix) + " " + it
        } ?: ctx.getString(R.string.media_no_limit)

        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_ALBUM, appName)
            .putString(MediaMetadata.METADATA_KEY_TITLE, "$speed $unit")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, limitText)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, System.currentTimeMillis())
            .build()

        try {
            s.setMetadata(metadata)
        } catch (_: Exception) { }
    }
}
