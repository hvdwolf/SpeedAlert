package xyz.hvdw.speedalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class ServiceNotification(private val ctx: Context) {

    companion object {
        const val CHANNEL_ID = "driving_service"
    }

    private val channelName: String
        get() = ctx.getString(R.string.channel_name)

    private val channelDesc: String
        get() = ctx.getString(R.string.channel_description)

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // Android 13+ requires a visible channel for POST_NOTIFICATIONS permission
            val importance =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    NotificationManager.IMPORTANCE_DEFAULT
                else
                    NotificationManager.IMPORTANCE_LOW

            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                importance
            ).apply {
                description = channelDesc
                setShowBadge(false)
            }


            val mgr = ctx.getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }
    }

    fun createNotification(): Notification =
        NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle(ctx.getString(R.string.notif_running_title))
            .setContentText(ctx.getString(R.string.notif_running_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // for pre-Oreo
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(
                        NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                    )
                }
            }
            .build()
}
