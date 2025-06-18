package se.premex.mcp.screenshot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.util.Pair

object NotificationUtils {
    private const val NOTIFICATION_CHANNEL_ID = "se.premex.mcp.screenshot.channel"
    private const val NOTIFICATION_CHANNEL_NAME = "Screenshot Service"
    private const val NOTIFICATION_ID = 1337

    fun getNotification(context: Context): Pair<Int, Notification> {
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Channel for screenshot capture service"
            channel.setShowBadge(false)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Build the notification
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Screenshot Capture")
            .setContentText("Screen capture service is running")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        return Pair(NOTIFICATION_ID, notification)
    }
}
