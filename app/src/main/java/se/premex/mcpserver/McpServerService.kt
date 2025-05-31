package se.premex.mcpserver

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import se.premex.adserver.mcp.ads.appendSmsTools

class McpServerService : Service() {
    companion object {
        private const val TAG = "McpServerService"
        private const val NOTIFICATION_ID = 1001
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? =
        null
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Initializing McpServerService")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create a pending intent for the notification that will bring existing activity to front
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Start foreground service immediately with the initial notification
        val initialNotification = createNotification(
            "Starting MCP server",
            "Initializing server...",
            pendingIntent
        )

        startForeground(NOTIFICATION_ID, initialNotification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: Starting service with id $startId")

        // Launch server initialization in a background coroutine
        serviceScope.launch {
            Log.d(TAG, "onStartCommand: Launching coroutine to start server")
            startMcpServer()
        }

        return START_STICKY
    }

    private fun startMcpServer() {
        Log.i(TAG, "startMcpServer: Attempting to start MCP server")
        try {
            // First try with localhost (most compatible)
            Log.d(TAG, "startMcpServer: Trying with localhost (127.0.0.1)")
            startServerWithHost("127.0.0.1", 3001)
            Log.i(TAG, "startMcpServer: Successfully started server on localhost")
        } catch (e: Exception) {
            Log.w(TAG, "startMcpServer: Failed with localhost, trying 0.0.0.0", e)
            try {
                // If localhost fails, try with all interfaces (may fail due to Android restrictions)
                startServerWithHost("0.0.0.0", 3001)
                Log.i(TAG, "startMcpServer: Successfully started server on all interfaces")
            } catch (e: Exception) {
                val errorMessage = "Failed to start server: ${e.message}"
                Log.e(TAG, "startMcpServer: Failed to start server on any interface", e)
                updateNotification("MCP Server Error", errorMessage)
            }
        }
    }

    private fun startServerWithHost(host: String, port: Int) {
        Log.d(TAG, "startServerWithHost: Configuring server on $host:$port")

        try {
            server = embeddedServer(CIO, host = host, port = port) {
                mcp {
                    return@mcp configureServer()
                }
            }

            Log.d(TAG, "startServerWithHost: Server configured, attempting to start")
            server?.start(wait = false)

            val successMessage = if (host == "127.0.0.1") {
                "Server running on localhost:$port (local device only)"
            } else {
                "Server running on $host:$port (accessible from network)"
            }

            Log.i(TAG, "startServerWithHost: $successMessage")

            // Create PendingIntent for notification with single activity flags
            val activityIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                activityIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            updateNotification("MCP Server Running", successMessage, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "startServerWithHost: Failed to start server", e)
            throw e
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: Stopping MCP server service")
        try {
            server?.stop(1000, 2000)
            Log.d(TAG, "onDestroy: Server stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy: Error stopping server", e)
        }

        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(
        title: String,
        content: String,
        contentIntent: PendingIntent? = null
    ): Notification {
        Log.d(TAG, "createNotification: Creating notification with title: $title")

        val builder = NotificationCompat.Builder(this, McpServerApplication.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        contentIntent?.let {
            builder.setContentIntent(it)
        }

        return builder.build()
    }

    private fun updateNotification(
        title: String,
        content: String,
        contentIntent: PendingIntent? = null
    ) {
        Log.d(TAG, "updateNotification: Updating notification with title: $title")
        val notification = createNotification(title, content, contentIntent)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun configureServer(): Server {
        val server = Server(
            Implementation(
                name = "mcp-kotlin test server",
                version = "0.1.0"
            ),
            ServerOptions(
                capabilities = ServerCapabilities(
                    prompts = ServerCapabilities.Prompts(listChanged = true),
                    resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                    tools = ServerCapabilities.Tools(listChanged = true),
                )
            )
        )

        appendSmsTools(
            server = server
        )
        //appendAdTools(
        //    server = server,
        //    clientId = "da9f87c34f4641a4a2bdace0ff4895fe",
        //)

        return server
    }
}

