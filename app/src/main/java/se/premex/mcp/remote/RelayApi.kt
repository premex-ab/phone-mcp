package se.premex.mcp.remote

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** An MCP client currently authorized for this device. */
data class PairedClientInfo(
    val clientId: String,
    val name: String?,
)

/** Small HTTP client for the relay's device API (registration + pairing codes). */
object RelayApi {

    class RelayException(message: String) : Exception(message)

    /**
     * Returns deviceId to deviceSecret. [trialAnchor] is a stable hash of a
     * hardware identifier so re-registrations from the same phone share one
     * free-trial window.
     */
    fun registerDevice(relayUrl: String, name: String, trialAnchor: String?): Pair<String, String> {
        val response = post(
            url = "$relayUrl/api/devices/register",
            body = JSONObject().put("name", name).putOpt("trialAnchor", trialAnchor).toString(),
            headers = mapOf("Content-Type" to "application/json"),
        )
        val json = JSONObject(response)
        return json.getString("deviceId") to json.getString("deviceSecret")
    }

    /** Current remote-access entitlement: status ("trial"/"paid"/"grace"/"expired") to activeUntil ISO instant. */
    fun getEntitlement(relayUrl: String, deviceId: String, deviceSecret: String): Pair<String, String> {
        val response = request(
            method = "GET",
            url = "$relayUrl/api/devices/$deviceId/entitlement",
            body = null,
            headers = mapOf("X-Device-Secret" to deviceSecret),
        )
        val json = JSONObject(response)
        return json.getString("status") to json.getString("activeUntil")
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

    /** Clients that currently hold live tokens for this device. */
    fun listClients(relayUrl: String, deviceId: String, deviceSecret: String): List<PairedClientInfo> {
        val response = request(
            method = "GET",
            url = "$relayUrl/api/devices/$deviceId/clients",
            body = null,
            headers = mapOf("X-Device-Secret" to deviceSecret),
        )
        val clients = JSONObject(response).getJSONArray("clients")
        return buildList {
            for (i in 0 until clients.length()) {
                val client = clients.getJSONObject(i)
                add(
                    PairedClientInfo(
                        clientId = client.getString("clientId"),
                        name = client.optString("name").takeIf { it.isNotEmpty() && it != "null" },
                    )
                )
            }
        }
    }

    /**
     * Submit a Play purchase token for server-side verification.
     * Returns status to activeUntil like [getEntitlement].
     */
    fun submitPurchase(
        relayUrl: String,
        deviceId: String,
        deviceSecret: String,
        purchaseToken: String,
    ): Pair<String, String> {
        val response = post(
            url = "$relayUrl/api/devices/$deviceId/subscription",
            body = JSONObject().put("purchaseToken", purchaseToken).toString(),
            headers = mapOf(
                "Content-Type" to "application/json",
                "X-Device-Secret" to deviceSecret,
            ),
        )
        val json = JSONObject(response)
        return json.getString("status") to json.getString("activeUntil")
    }

    /** Revoke every token binding this client to this device. */
    fun revokeClient(relayUrl: String, deviceId: String, deviceSecret: String, clientId: String) {
        request(
            method = "DELETE",
            url = "$relayUrl/api/devices/$deviceId/clients/$clientId",
            body = null,
            headers = mapOf("X-Device-Secret" to deviceSecret),
        )
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String =
        request("POST", url, body, headers)

    private fun request(method: String, url: String, body: String?, headers: Map<String, String>): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }

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
