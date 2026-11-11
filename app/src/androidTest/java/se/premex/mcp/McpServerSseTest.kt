package se.premex.mcp

import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import se.premex.mcp.auth.AuthRepository

@RunWith(AndroidJUnit4::class)
class McpServerSseTest {
    @Test
    fun idleSessionStillHandlesRequestsAndReconnectKeepsAuthentication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val serviceIntent = Intent(context, McpServerService::class.java)
        val token = AuthRepository(context.applicationContext).currentToken()

        try {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                activity.onActivity { ContextCompat.startForegroundService(it, serviceIntent) }
                connectWhenServerIsReady(token).use { stream ->
                    val endpoint = stream.awaitEndpoint()
                    initialize(stream, endpoint, token)
                    activity.moveToState(Lifecycle.State.CREATED)

                    // Read multiple periodic heartbeats, not just an immediate one. Do not
                    // depend on whether the endpoint or the first heartbeat arrives first.
                    repeat(3) { stream.awaitEvent { it.comments.contains("heartbeat") } }
                    assertToolsRespond(stream, endpoint, token)
                }

                // Simulate a client reconnecting after its connection was lost. The
                // existing bearer token must work for a complete new MCP session.
                repeat(2) {
                    openSseConnection(token).use { stream ->
                        assertEquals(HttpURLConnection.HTTP_OK, stream.connection.responseCode)
                        val endpoint = stream.awaitEndpoint()
                        initialize(stream, endpoint, token)
                        assertToolsRespond(stream, endpoint, token)
                    }
                }

                assertUnauthorized(null)
                assertUnauthorized("invalid-token")
            }
        } finally {
            context.stopService(serviceIntent)
        }
    }

    private fun connectWhenServerIsReady(token: String): SseConnection {
        val deadline = SystemClock.elapsedRealtime() + SERVER_START_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                val stream = openSseConnection(token)
                if (stream.connection.responseCode == HttpURLConnection.HTTP_OK) return stream
                stream.close()
            } catch (_: IOException) {
                // The foreground service may still be opening its listening socket.
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("SSE server did not become ready within the timeout")
    }

    private fun openSseConnection(token: String?): SseConnection {
        val connection = newConnection("/sse", token).apply {
            setRequestProperty("Accept", "text/event-stream")
        }
        try {
            return SseConnection(connection).also { connection.responseCode }
        } catch (error: Exception) {
            connection.disconnect()
            throw error
        }
    }

    private fun newConnection(path: String, token: String?) =
        (URL("http://127.0.0.1:3001$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 1_000
            readTimeout = EVENT_TIMEOUT_MILLIS.toInt()
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

    private fun assertUnauthorized(token: String?) {
        openSseConnection(token).use {
            assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, it.connection.responseCode)
        }
    }

    private fun initialize(stream: SseConnection, endpoint: String, token: String) {
        post(endpoint, token, """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"idle-regression","version":"1"}}}""")
        assertTrue(stream.awaitResponse(1).has("result"))
        post(endpoint, token, """{"jsonrpc":"2.0","method":"notifications/initialized"}""")
    }

    private fun assertToolsRespond(stream: SseConnection, endpoint: String, token: String) {
        post(endpoint, token, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        assertTrue(stream.awaitResponse(2).getJSONObject("result").has("tools"))
    }

    private fun post(endpoint: String, token: String, body: String) {
        val connection = newConnection(endpoint, token)
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            assertEquals(HttpURLConnection.HTTP_ACCEPTED, connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }

    private data class SseEvent(val event: String?, val data: String, val comments: List<String>)

    private class SseConnection(val connection: HttpURLConnection) : Closeable {
        // HttpURLConnection decodes HTTP chunk framing before the SSE parser sees it.
        private val reader: BufferedReader by lazy { connection.inputStream.bufferedReader() }

        fun awaitEndpoint(): String {
            assertTrue(connection.contentType.startsWith("text/event-stream"))
            return awaitEvent { it.event == "endpoint" }.data.also {
                assertTrue("Unexpected MCP endpoint", it.startsWith("/message?sessionId="))
            }
        }

        fun awaitResponse(id: Int): JSONObject = JSONObject(
            awaitEvent {
                it.event == "message" && JSONObject(it.data).optInt("id", -1) == id
            }.data
        )

        fun awaitEvent(predicate: (SseEvent) -> Boolean): SseEvent {
            val deadline = SystemClock.elapsedRealtime() + EVENT_TIMEOUT_MILLIS
            while (SystemClock.elapsedRealtime() < deadline) {
                val event = readEvent(deadline)
                if (predicate(event)) return event
            }
            throw AssertionError("Expected SSE event did not arrive before the deadline")
        }

        private fun readEvent(deadline: Long): SseEvent {
            var event: String? = null
            val data = mutableListOf<String>()
            val comments = mutableListOf<String>()
            while (SystemClock.elapsedRealtime() < deadline) {
                val line = reader.readLine() ?: throw AssertionError("SSE stream closed unexpectedly")
                when {
                    line.isEmpty() -> return SseEvent(event, data.joinToString("\n"), comments)
                    line.startsWith(":") -> comments.add(line.substring(1).removePrefix(" "))
                    line.startsWith("event:") -> event = line.substring(6).removePrefix(" ")
                    line.startsWith("data:") -> data.add(line.substring(5).removePrefix(" "))
                }
            }
            throw AssertionError("SSE event did not finish before the deadline")
        }

        override fun close() = connection.disconnect()
    }

    companion object {
        private const val SERVER_START_TIMEOUT_MILLIS = 10_000L
        // Production heartbeats run every 15 seconds; allow scheduling headroom.
        private const val EVENT_TIMEOUT_MILLIS = 25_000L
    }
}
