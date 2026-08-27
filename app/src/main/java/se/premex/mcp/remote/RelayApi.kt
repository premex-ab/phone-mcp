package se.premex.mcp.remote

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Small HTTP client for the relay's device API (registration + pairing codes). */
object RelayApi {

    class RelayException(message: String) : Exception(message)

    /** Returns deviceId to deviceSecret. */
    fun registerDevice(relayUrl: String, name: String): Pair<String, String> {
        val response = post(
            url = "$relayUrl/api/devices/register",
            body = JSONObject().put("name", name).toString(),
            headers = mapOf("Content-Type" to "application/json"),
        )
        val json = JSONObject(response)
        return json.getString("deviceId") to json.getString("deviceSecret")
    }

    /** Returns the pairing code to its validity in seconds. */
    fun requestPairingCode(relayUrl: String, deviceId: String, deviceSecret: String): Pair<String, Long> {
        val response = post(
            url = "$relayUrl/api/devices/$deviceId/pairing-code",
            body = "",
            headers = mapOf("X-Device-Secret" to deviceSecret),
        )
        val json = JSONObject(response)
        return json.getString("code") to json.optLong("expiresInSeconds", 600L)
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray()) }

            val status = connection.responseCode
            val text = (if (status < 400) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (status >= 400) {
                throw RelayException("Relay error $status: ${text.take(200)}")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }
}
