package se.premex.mcp.remote

import android.util.Base64
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps an outbound WebSocket tunnel open to the relay and forwards incoming
 * requests to the local MCP server on 127.0.0.1.
 *
 * Frame protocol (JSON text frames) — mirror of TunnelProtocol.kt in the
 * relay repo (premex-ab/phonemcp-relay); documented in PROTOCOL.md:
 *   -> hello {deviceId, secret}          <- hello_ok | error {message}
 *   <- req {id, method, path, headers, body?}
 *   -> head {id, status, headers}, chunk {id, data(base64)}, end {id}
 *   <- cancel {id}
 *
 * The relay never sees the local auth token: the Authorization header on
 * forwarded requests is replaced with this device's own local token.
 */
class TunnelClient(
    val relayUrl: String,
    val deviceId: String,
    private val deviceSecret: String,
    private val localPort: Int,
    private val localAuthToken: String,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "TunnelClient"
        private const val CHUNK_SIZE = 16 * 1024
    }

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    private var runJob: Job? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val wsUrl: String = relayUrl
        .replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://")
        .trimEnd('/') + "/tunnel"

    fun start() {
        if (runJob?.isActive == true) return
        runJob = scope.launch { runLoop() }
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
        _connected.value = false
        client.close()
    }

    private suspend fun runLoop() {
        var backoffMs = 2_000L
        while (currentCoroutineContext().isActive) {
            try {
                connectOnce()
                backoffMs = 2_000L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Tunnel connection to $wsUrl failed: ${e.message}")
            }
            _connected.value = false
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
        }
    }

    private suspend fun connectOnce() {
        client.webSocket(wsUrl) {
            val sendMutex = Mutex()
            suspend fun sendFrame(json: JSONObject) {
                sendMutex.withLock { send(Frame.Text(json.toString())) }
            }

            sendFrame(
                JSONObject()
                    .put("type", "hello")
                    .put("deviceId", deviceId)
                    .put("secret", deviceSecret)
            )
            val greeting = (incoming.receive() as? Frame.Text)?.readText()
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (greeting?.optString("type") != "hello_ok") {
                throw IOException(
                    "Relay rejected tunnel: ${greeting?.optString("message").orEmpty().ifEmpty { "no hello_ok" }}"
                )
            }
            Log.i(TAG, "Tunnel connected to $wsUrl")
            _connected.value = true

            val activeRequests = ConcurrentHashMap<String, RequestHandle>()
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val message = runCatching { JSONObject(frame.readText()) }.getOrNull() ?: continue
                    when (message.optString("type")) {
                        "req" -> {
                            val id = message.optString("id")
                            val handle = RequestHandle()
                            activeRequests[id] = handle
                            handle.job = launch(Dispatchers.IO) {
                                try {
                                    forwardRequest(id, message, handle, ::sendFrame)
                                } finally {
                                    activeRequests.remove(id)
                                }
                            }
                        }

                        "cancel" -> activeRequests.remove(message.optString("id"))?.abort()
                    }
                }
            } finally {
                _connected.value = false
                activeRequests.values.forEach { it.abort() }
            }
        }
    }

    private class RequestHandle {
        @Volatile
        var job: Job? = null

        @Volatile
        var connection: HttpURLConnection? = null

        fun abort() {
            // disconnect() unblocks a stream read stuck on an idle SSE connection
            runCatching { connection?.disconnect() }
            job?.cancel()
        }
    }

    private suspend fun forwardRequest(
        id: String,
        message: JSONObject,
        handle: RequestHandle,
        sendFrame: suspend (JSONObject) -> Unit,
    ) {
        try {
            val path = message.optString("path", "/")
            val connection =
                URL("http://127.0.0.1:$localPort$path").openConnection() as HttpURLConnection
            handle.connection = connection
            connection.requestMethod = message.optString("method", "GET")
            connection.connectTimeout = 10_000
            connection.readTimeout = 0 // SSE streams stay open indefinitely

            message.optJSONObject("headers")?.let { headers ->
                headers.keys().forEach { key ->
                    if (!key.equals("Authorization", ignoreCase = true)) {
                        connection.setRequestProperty(key, headers.getString(key))
                    }
                }
            }
            connection.setRequestProperty("Authorization", "Bearer $localAuthToken")

            if (message.has("body") && !message.isNull("body")) {
                connection.doOutput = true
                connection.outputStream.use { it.write(message.getString("body").toByteArray()) }
            }

            val status = connection.responseCode
            val responseHeaders = JSONObject()
            connection.headerFields.forEach { (key, values) ->
                if (key != null && values.isNotEmpty()) responseHeaders.put(key, values.first())
            }
            sendFrame(
                JSONObject()
                    .put("type", "head")
                    .put("id", id)
                    .put("status", status)
                    .put("headers", responseHeaders)
            )

            val stream = if (status < 400) connection.inputStream else connection.errorStream
            if (stream != null) {
                val buffer = ByteArray(CHUNK_SIZE)
                while (true) {
                    val read = try {
                        stream.read(buffer)
                    } catch (e: IOException) {
                        -1 // connection closed (local server stopped, or abort())
                    }
                    if (read < 0) break
                    if (read > 0) {
                        sendFrame(
                            JSONObject()
                                .put("type", "chunk")
                                .put("id", id)
                                .put("data", Base64.encodeToString(buffer.copyOf(read), Base64.NO_WRAP))
                        )
                    }
                }
            }
            sendFrame(JSONObject().put("type", "end").put("id", id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Forwarding request $id failed: ${e.message}")
            runCatching {
                sendFrame(
                    JSONObject()
                        .put("type", "error")
                        .put("id", id)
                        .put("message", e.message ?: "forward failed")
                )
            }
        } finally {
            runCatching { handle.connection?.disconnect() }
        }
    }
}
