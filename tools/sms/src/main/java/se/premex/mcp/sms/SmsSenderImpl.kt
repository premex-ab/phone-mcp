package se.premex.mcp.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import javax.inject.Inject

class SmsSenderImpl @Inject constructor(
    private val context: Context
) : SmsSender {
    override fun sendSms(phoneNumber: String, message: String): Boolean {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Create a PendingIntent for delivery status
            val sentIntent = PendingIntent.getBroadcast(
                context, 0, Intent("SMS_SENT"), PendingIntent.FLAG_IMMUTABLE
            )

            if (message.length > 160) {
                // Split message for longer messages
                val parts = smsManager.divideMessage(message)
                val sentIntents = ArrayList<PendingIntent>().apply {
                    for (i in parts.indices) add(sentIntent)
                }
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
            } else {
                // Send a simple message
                smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

