package se.premex.mcp.smsintent

import android.R
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import java.util.Random
import javax.inject.Inject

class SmsIntentSenderImpl @Inject constructor(
    private val context: Context
) : SmsIntentSender {
    
    companion object {
        private const val CHANNEL_ID = "sms_intent_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "SmsIntentSender"
    }
    
    override fun sendSmsIntent(phoneNumber: String, message: String): Boolean {
        // Create intent with ACTION_SENDTO and proper URI scheme
        val uri = "smsto:$phoneNumber".toUri()
        val sendIntent = Intent(Intent.ACTION_SENDTO, uri)

        // Add message body
        sendIntent.putExtra("sms_body", message)

        // Add new task flag since we're starting from outside an Activity context
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Check if app is in foreground
        if (isAppInForeground()) {
            try {
                // Direct approach only when app is in foreground
                context.startActivity(sendIntent)
                Log.d(TAG, "Started SMS activity directly")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SMS activity directly: ${e.message}")
                // Fall back to notification
                showSmsNotification(sendIntent, phoneNumber)
                return true
            }
        } else {
            // App is in background, use notification directly
            Log.d(TAG, "App is in background, showing notification to send SMS")
            showSmsNotification(sendIntent, phoneNumber)
            return true
        }
    }
    
    private fun isAppInForeground(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = context.packageName

        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    private fun showSmsNotification(smsIntent: Intent, phoneNumber: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android O and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for sending SMS messages"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create pending intent
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE

        val pendingIntent = PendingIntent.getActivity(
            context,
            Random().nextInt(), // Random request code to ensure uniqueness
            smsIntent,
            pendingIntentFlags
        )
        
        // Build the notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Send SMS")
            .setContentText("Tap to send SMS to $phoneNumber")
            .setSmallIcon(R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        // Show notification
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "SMS notification displayed")
    }
}
