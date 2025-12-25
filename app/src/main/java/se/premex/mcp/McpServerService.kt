package se.premex.mcp

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.bearer
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.auth.AuthRepository
import se.premex.mcp.data.ToolPreferencesRepository
import se.premex.mcp.data.ServerPreferencesRepository
import se.premex.mcp.di.ToolService
import javax.inject.Inject
import kotlin.collections.set

@AndroidEntryPoint
class McpServerService : Service() {
    companion object {
        var isRunning = mutableStateOf(false)
        private const val TAG = "McpServerService"
        private const val NOTIFICATION_ID = 1001

        // Log tag prefixes for better filtering
        private const val LOG_PREFIX_LIFECYCLE = "Lifecycle"
        private const val LOG_PREFIX_SERVER = "Server"
        private const val LOG_PREFIX_NOTIFICATION = "Notification"
        private const val LOG_PREFIX_TOOLS = "Tools"
        private const val LOG_PREFIX_TRANSPORT = "Transport"
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? =
        null
    private var notificationManager: NotificationManager? = null

    // Tool states will be loaded from the repository
    private var toolStates: Map<String, Boolean> = emptyMap()

    @Inject
    lateinit var toolService: ToolService

    @Inject
    lateinit var toolPreferencesRepository: ToolPreferencesRepository

    @Inject
    lateinit var serverPreferencesRepository: ServerPreferencesRepository

    @Inject
    lateinit var availableTools: Set<@JvmSuppressWildcards McpTool>

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate() {
        isRunning.value = true
        super.onCreate()

        Log.i(TAG, "$LOG_PREFIX_LIFECYCLE: Service onCreate started")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Log.d(TAG, "$LOG_PREFIX_LIFECYCLE: NotificationManager initialized")

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
        Log.i(
            TAG,
            "$LOG_PREFIX_LIFECYCLE: Service started in foreground with notification ID $NOTIFICATION_ID"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(
            TAG,
            "$LOG_PREFIX_LIFECYCLE: onStartCommand called with startId=$startId, flags=$flags"
        )

        // Launch server initialization in a background coroutine
        Log.d(TAG, "$LOG_PREFIX_SERVER: Launching server initialization in background coroutine")
        serviceScope.launch {
            // Load tool states from the repository (suspend) before starting server
            toolStates = toolPreferencesRepository.getToolEnabledStates().first()
            Log.d(TAG, "$LOG_PREFIX_TOOLS: Loaded tool states from repository: $toolStates")

            // Call suspend startMcpServer within coroutine scope
            startMcpServer()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "$LOG_PREFIX_LIFECYCLE: Service onDestroy called")
        try {
            Log.d(TAG, "$LOG_PREFIX_SERVER: Attempting to stop server")
            server?.stop(1000, 2000)
            Log.i(TAG, "$LOG_PREFIX_SERVER: Server stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "$LOG_PREFIX_SERVER: Error stopping server", e)
        }

        Log.d(TAG, "$LOG_PREFIX_LIFECYCLE: Cancelling service job")
        serviceJob.cancel()
        super.onDestroy()
        Log.i(TAG, "$LOG_PREFIX_LIFECYCLE: Service destroyed")
        isRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "$LOG_PREFIX_LIFECYCLE: onBind called, returning null")
        return null
    }

    private suspend fun startMcpServer() {
        Log.i(TAG, "$LOG_PREFIX_SERVER: Attempting to start MCP server")
        try {
            val serverConfig = serverPreferencesRepository.getServerConfig().first()
            Log.d(
                TAG,
                "$LOG_PREFIX_SERVER: Loaded server config - host: ${serverConfig.host}, port: ${serverConfig.port}"
            )
            startServerWithHost(serverConfig.host, serverConfig.port)
            Log.i(TAG, "$LOG_PREFIX_SERVER: Successfully started server")
        } catch (e: Exception) {
            val errorMessage = "Failed to start server: ${e.message}"
            Log.e(TAG, "$LOG_PREFIX_SERVER: Failed to start server", e)
            updateNotification("MCP Server Error", errorMessage)
        }
    }

    private fun startServerWithHost(host: String, port: Int) {
        Log.d(TAG, "$LOG_PREFIX_SERVER: Configuring server on $host:$port")

        try {
            server = runSseMcpServerWithPlainConfiguration(host = host, port = port)

            // Get WiFi IP address to show in notification
            val wifiIp = NetworkUtils.getWifiIpAddress(this)
            Log.d(TAG, "$LOG_PREFIX_SERVER: Obtained WiFi IP address: $wifiIp")

            // Get connection instructions including the authentication token
            val authInstructions = authRepository.getConnectionInstructions()

            val successMessage = if (host == "127.0.0.1") {
                if (wifiIp != null) {
                    "Server running on $wifiIp:$port\n$authInstructions"
                } else {
                    "Server running on localhost:$port (local device only)\n$authInstructions"
                }
            } else {
                if (wifiIp != null) {
                    "Server running on $wifiIp:$port\n$authInstructions"
                } else {
                    "Server running on $host:$port\n$authInstructions"
                }
            }

            Log.i(TAG, "$LOG_PREFIX_SERVER: $successMessage")

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
            Log.e(TAG, "$LOG_PREFIX_SERVER: Failed to start server on $host:$port", e)
            throw e
        }
    }

    private fun createNotification(
        title: String,
        content: String,
        contentIntent: PendingIntent? = null
    ): Notification {
        Log.d(TAG, "$LOG_PREFIX_NOTIFICATION: Creating notification with title: $title")

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
        Log.d(
            TAG,
            "$LOG_PREFIX_NOTIFICATION: Updating notification with title: $title, content: $content"
        )
        val notification = createNotification(title, content, contentIntent)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun configureServer(): Server {
        Log.d(TAG, "$LOG_PREFIX_SERVER: Configuring MCP server with tools")
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

        // Add tools based on their enabled state from the repository
        Log.d(TAG, "$LOG_PREFIX_TOOLS: Configuring ${availableTools.size} available tools")
        var enabledCount = 0
        for (tool in availableTools) {
            val isEnabled = toolStates[tool.id] ?: tool.enabledByDefault

            if (isEnabled) {
                Log.d(TAG, "$LOG_PREFIX_TOOLS: Enabling tool ${tool.id}")
                tool.configure(server = server)
                enabledCount++
            } else {
                Log.d(TAG, "$LOG_PREFIX_TOOLS: Tool ${tool.id} is disabled, skipping")
            }
        }
        Log.i(TAG, "$LOG_PREFIX_SERVER: Server configured with $enabledCount enabled tools")

        return server
    }


    private fun runSseMcpServerWithPlainConfiguration(
        host: String,
        port: Int
    ): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        val servers = ConcurrentMap<String, Server>()
        val transports = ConcurrentMap<String, SseServerTransport>()
        Log.i(TAG, "$LOG_PREFIX_SERVER: Starting SSE server on port $port")
        Log.d(TAG, "$LOG_PREFIX_SERVER: Use inspector to connect to http://localhost:$port/sse")

        val server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
            embeddedServer(CIO, host = host, port = port) {
                Log.d(TAG, "$LOG_PREFIX_SERVER: Configuring server authentication and routes")

                authentication {
                    bearer(name = "bearer-auth") {
                        realm = "Ktor Server"
                        authenticate { tokenCredential ->
                            val authResult =
                                authRepository.validateBearerToken(tokenCredential.token)
                            if (authResult.isAuthenticated) {
                                Log.d(
                                    TAG,
                                    "$LOG_PREFIX_SERVER: Authentication successful. User ID: ${authResult.userId}"
                                )
                                authResult.userId?.let { UserIdPrincipal(it) }
                            } else {
                                Log.d(
                                    TAG,
                                    "$LOG_PREFIX_SERVER: Authentication failed: ${authResult.message}"
                                )
                                null
                            }
                        }
                    }
                }
                install(SSE)
                install(CORS) {
                    // Configure CORS settings as needed
                    anyHost() // Allow requests from any origin (for development only)
                    allowHeader(HttpHeaders.Authorization)
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(HttpHeaders.Accept)
                }

                routing {
                    authenticate("bearer-auth") {
                        sse("/sse") {
                            Log.d(TAG, "$LOG_PREFIX_TRANSPORT: New SSE connection established")
                            val transport = SseServerTransport("/message", this)
                            Log.d(
                                TAG,
                                "$LOG_PREFIX_TRANSPORT: Created SSE transport with sessionId: ${transport.sessionId}"
                            )

                            val server = configureServer()

                            servers[transport.sessionId] = server
                            transports[transport.sessionId] = transport
                            Log.d(
                                TAG,
                                "$LOG_PREFIX_SERVER: Added server for session ${transport.sessionId}"
                            )

                            server.onClose {
                                Log.i(
                                    TAG,
                                    "$LOG_PREFIX_SERVER: Server closed for session ${transport.sessionId}"
                                )
                                servers.remove(transport.sessionId)
                                transports.remove(transport.sessionId)
                                Log.d(
                                    TAG,
                                    "$LOG_PREFIX_SERVER: Removed server for session ${transport.sessionId}"
                                )
                            }

                            Log.d(
                                TAG,
                                "$LOG_PREFIX_SERVER: Connecting server to transport for session ${transport.sessionId}"
                            )
                            server.connect(transport)
                            Log.i(
                                TAG,
                                "$LOG_PREFIX_SERVER: Server successfully connected to transport"
                            )
                        }
                    }

                    post("/message") {
                        Log.e(
                            TAG,
                            "$LOG_PREFIX_TRANSPORT: Received POST request to /message endpoint"
                        )
                        val sessionId = try {
                            call.request.queryParameters["sessionId"]!!
                        } catch (e: Exception) {
                            Log.e(
                                TAG,
                                "$LOG_PREFIX_TRANSPORT: Missing sessionId in message request",
                                e
                            )
                            call.respond(HttpStatusCode.BadRequest, "Missing sessionId parameter")
                            return@post
                        }

                        Log.d(TAG, "$LOG_PREFIX_TRANSPORT: Received message for session $sessionId")

                        val transport = transports[sessionId]
                        if (transport == null) {
                            Log.w(TAG, "$LOG_PREFIX_TRANSPORT: Session not found: $sessionId")
                            call.respond(HttpStatusCode.NotFound, "Session not found")
                            return@post
                        }

                        try {
                            transport.handlePostMessage(call)
                            Log.v(
                                TAG,
                                "$LOG_PREFIX_TRANSPORT: Successfully handled message for session $sessionId"
                            )
                        } catch (e: Exception) {
                            Log.e(
                                TAG,
                                "$LOG_PREFIX_TRANSPORT: Error handling message for session $sessionId",
                                e
                            )
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                "Error processing message"
                            )
                        }
                    }
                }
            }.start(wait = false)
        Log.i(TAG, "$LOG_PREFIX_SERVER: Server successfully started on port $port")
        return server
    }
}
