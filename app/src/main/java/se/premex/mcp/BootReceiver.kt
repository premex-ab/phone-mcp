package se.premex.mcp

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import se.premex.mcp.data.ServerPreferencesRepository
import javax.inject.Inject

/**
 * Brings the MCP server back after a reboot when the user left it running —
 * a remote-access phone must not go dark because Android restarted.
 *
 * Newer Android versions refuse dataSync foreground services started from
 * BOOT_COMPLETED; in that case a tap-to-restart notification is posted
 * instead, so the user is one tap from being reachable again.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var serverPreferencesRepository: ServerPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val shouldRun = runBlocking { serverPreferencesRepository.serverShouldRun().first() }
        if (!shouldRun) return

        Log.i(TAG, "Boot completed and the server was running — restoring")
        try {
            val serviceIntent = Intent(context, McpServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            val blockedByPolicy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            if (!blockedByPolicy) Log.e(TAG, "Could not restart the server after boot", e)
            postRestartNotification(context)
        }
    }

    private fun postRestartNotification(context: Context) {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_AUTO_START_SERVER, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, McpServerApplication.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.restart_after_boot_title))
            .setContentText(context.getString(R.string.restart_after_boot_body))
            .setSmallIcon(R.drawable.ic_stat_server)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(BOOT_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "BootReceiver"
        private const val BOOT_NOTIFICATION_ID = 1003
    }
}
