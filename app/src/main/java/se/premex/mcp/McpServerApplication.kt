package se.premex.mcp

import android.app.Application
import android.os.Build
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.appfunctions.service.AppFunctionConfiguration
import se.premex.mcp.appfunctions.service.AppFunctionFactoryRegistration
import javax.inject.Inject

@HiltAndroidApp
class McpServerApplication : Application(), AppFunctionConfiguration.Provider {
    companion object {
        private const val TAG = "McpServerApplication"
        const val CHANNEL_ID = "mcp_server_channel"
    }

    @Inject
    lateinit var appFunctionFactoryRegistrations: Set<@JvmSuppressWildcards AppFunctionFactoryRegistration>

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .also { builder ->
                appFunctionFactoryRegistrations.forEach { it.register(builder) }
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "createNotificationChannel: Creating notification channel")

            val channel = NotificationChannel(
                CHANNEL_ID,
                "MCP Server Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for MCP Server status"
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.i(TAG, "Notification channel created")
        }
    }
}
