package se.premex.mcp

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.Closeable
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import se.premex.mcp.auth.AuthRepository

@RunWith(AndroidJUnit4::class)
class McpServerSseTest {
    @Test
    fun sseConnectionStaysOpenAndAuthenticatedReconnectSucceeds() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val serviceIntent = Intent(context, McpServerService::class.java)
        context.stopService(serviceIntent)

        val authRepository = AuthRepository(context.applicationContext)
        val token = authRepository.getConnectionInstructions()
            .substringAfter("'")
            .substringBefore("'")

        ContextCompat.startForegroundService(context, serviceIntent)

        try {
            connectWhenServerIsReady(token).use { initialConnection ->
                assertSseStreamRemainsOpen(initialConnection)
            }

            openSseConnection(token).use { reconnect ->
                assertEquals(HTTP_OK, reconnect.statusCode)
            }

            openSseConnection(token = null).use { missingAuthentication ->
                assertEquals(HTTP_UNAUTHORIZED, missingAuthentication.statusCode)
            }
        } finally {
            context.stopService(serviceIntent)
        }
    }

    private suspend fun connectWhenServerIsReady(token: String): RawHttpConnection =
        withTimeoutOrNull(SERVER_START_TIMEOUT_MILLIS) {
            while (true) {
                try {
                    val connection = openSseConnection(token)
                    if (connection.statusCode == HTTP_OK) {
                        return@withTimeoutOrNull connection
                    }
                    connection.close()
                } catch (_: Exception) {
                    // The foreground service may still be opening its listening socket.
                }
                delay(SERVER_RETRY_DELAY_MILLIS)
            }
            error("Unreachable")
        } ?: throw AssertionError("SSE server did not become ready within the timeout")

    private fun openSseConnection(token: String?): RawHttpConnection {
        val socket = Socket().apply {
            connect(InetSocketAddress(SERVER_HOST, SERVER_PORT), CONNECTION_TIMEOUT_MILLIS)
            soTimeout = STREAM_READ_TIMEOUT_MILLIS
        }
        val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.US_ASCII)
        writer.write("GET /sse HTTP/1.1\r\n")
        writer.write("Host: $SERVER_HOST:$SERVER_PORT\r\n")
        writer.write("Accept: text/event-stream\r\n")
        writer.write("Connection: keep-alive\r\n")
        token?.let { writer.write("Authorization: Bearer $it\r\n") }
        writer.write("\r\n")
        writer.flush()

        val reader = socket.getInputStream().bufferedReader()
        val statusLine = reader.readLine()
            ?: throw AssertionError("Server closed before sending an HTTP status")
        val statusCode = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw AssertionError("Invalid HTTP status line: $statusLine")
        while (true) {
            val header = reader.readLine()
                ?: throw AssertionError("Server closed while sending HTTP headers")
            if (header.isEmpty()) {
                break
            }
        }
        return RawHttpConnection(socket, reader, statusCode)
    }

    private fun assertSseStreamRemainsOpen(connection: RawHttpConnection) {
        assertEquals(HTTP_OK, connection.statusCode)

        var receivedHeartbeat = false
        while (true) {
            val line = connection.reader.readLine()
                ?: throw AssertionError("SSE stream closed before the endpoint event")
            if (line == ": heartbeat") {
                receivedHeartbeat = true
            }
            if (line.startsWith("data: /message?sessionId=")) {
                break
            }
        }
        assertTrue("SSE stream did not emit a heartbeat", receivedHeartbeat)

        try {
            while (true) {
                if (connection.reader.readLine() == null) {
                    fail("SSE stream closed instead of remaining open")
                }
            }
        } catch (_: SocketTimeoutException) {
            // No data before the read timeout means the stream is still open.
        }
    }

    private data class RawHttpConnection(
        val socket: Socket,
        val reader: BufferedReader,
        val statusCode: Int
    ) : Closeable {
        override fun close() {
            socket.close()
        }
    }

    companion object {
        private const val SERVER_HOST = "127.0.0.1"
        private const val SERVER_PORT = 3001
        private const val HTTP_OK = 200
        private const val HTTP_UNAUTHORIZED = 401
        private const val SERVER_START_TIMEOUT_MILLIS = 10_000L
        private const val SERVER_RETRY_DELAY_MILLIS = 100L
        private const val CONNECTION_TIMEOUT_MILLIS = 1_000
        private const val STREAM_READ_TIMEOUT_MILLIS = 1_000
    }
}
