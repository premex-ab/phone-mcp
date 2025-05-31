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
import dagger.hilt.android.AndroidEntryPoint
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
import se.premex.adserver.mcp.ads.appendAdTools
import se.premex.mcp.core.tool.McpTool
import se.premex.mcpserver.di.ToolService
import javax.inject.Inject

@AndroidEntryPoint
class McpServerService : Service() {
    companion object {
        private const val TAG = "McpServerService"
        private const val NOTIFICATION_ID = 1001

        // Intent extra keys for tool configuration
        const val EXTRA_TOOL_STATES = "toolsStates"
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var notificationManager: NotificationManager? = null

    // Tool states from the intent or default values
    private var toolStates: Map<String, Boolean> = emptyMap()

    @Inject
    lateinit var toolService: ToolService

    @Inject
    lateinit var availableTools: Set<@JvmSuppressWildcards McpTool>

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

        // Read tool states from intent
        intent?.let {
            @Suppress("UNCHECKED_CAST")
            val receivedToolStates = it.getSerializableExtra(EXTRA_TOOL_STATES) as? HashMap<String, Boolean>

            if (receivedToolStates != null) {
                toolStates = receivedToolStates.toMap()
                Log.d(TAG, "onStartCommand: Received tool states: $toolStates")
            } else {
                // If no tool states were passed, use the current tool states from the ToolService
                toolStates = toolService.toolEnabledStates.value
                Log.d(TAG, "onStartCommand: Using default tool states: $toolStates")
            }
        }

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
            // Always bind to all interfaces (0.0.0.0)
            Log.d(TAG, "startMcpServer: Starting server on all interfaces (0.0.0.0)")
            startServerWithHost("0.0.0.0", 3001)
            Log.i(TAG, "startMcpServer: Successfully started server on all interfaces")
        } catch (e: Exception) {
            val errorMessage = "Failed to start server: ${e.message}"
            Log.e(TAG, "startMcpServer: Failed to start server", e)
            updateNotification("MCP Server Error", errorMessage)
        }
    }

    private fun startServerWithHost(host: String, port: Int) {
        Log.d(TAG, "startServerWithHost: Configuring server on $host:$port")

        try {
            // Konfigurera MCP-servern en gång utanför embeddedServer
            val mcpServer = configureServer()

            server = embeddedServer(CIO, host = host, port = port) {
                mcp {
                    // Returnera den förkonfigurerade serverinstansen
                    return@mcp mcpServer
                }
            }

            Log.d(TAG, "startServerWithHost: Server configured, attempting to start")
            server?.start(wait = false)

            // Get WiFi IP address to show in notification
            val wifiIp = NetworkUtils.getWifiIpAddress(this)

            val successMessage = if (host == "127.0.0.1") {
                if (wifiIp != null) {
                    "Server running on $wifiIp:$port"
                } else {
                    "Server running on localhost:$port (local device only)"
                }
            } else {
                if (wifiIp != null) {
                    "Server running on $wifiIp:$port"
                } else {
                    "Server running on $host:$port"
                }
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
        val server: Server = Server(
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

        // Add tools based on their enabled state
        for (tool in availableTools) {
            val isEnabled = toolStates[tool.id] ?: tool.enabledByDefault

            if (isEnabled) {
                tool.configure(server = server)
                when (tool.id) {
                    "sms" -> {
                        Log.d(TAG, "configureServer: Adding SMS tools")
                        appendSmsTools(server = server)
                    }
                    "ads" -> {
                        Log.d(TAG, "configureServer: Adding Ads tools")
                        appendAdTools(
                            server = server,
                            clientId = "da9f87c34f4641a4a2bdace0ff4895fe",
                        )
                    }
                    else -> {
                        Log.d(TAG, "configureServer: Unknown tool ID: ${tool.id}")
                    }
                }
            } else {
                Log.d(TAG, "configureServer: Tool ${tool.id} is disabled, skipping")
            }
        }

        return server
    }
}

