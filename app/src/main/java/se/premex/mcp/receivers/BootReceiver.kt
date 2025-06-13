package se.premex.mcp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint

/**
 * Broadcast receiver that starts the MCP service when the device boots
 * if auto-start is enabled and the service was running before shutdown
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"


    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.i(TAG, "Received ${intent.action}, checking if service should be started")
            // Let the repository handle the system reboot logic, including coroutine scope and service starting
            // serviceStateRepository.onSystemReboot(McpServerService::class.java)
        }
    }
}
