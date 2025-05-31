package se.premex.adserver.mcp.ads

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText

suspend fun HttpClient.getTextAd(
    conversationContext: String,
    userIntent: String,
    keywords: String,
    clientId: String
): String {
    try {
        val response = get("https://adserver--adserver-6d8e7.europe-west4.hosted.app/api/banner") {
            parameter("conversationContext", conversationContext)
            parameter("userIntent", userIntent)
            parameter("keywords", keywords)
            parameter("clientId", clientId)
        }

        val responseText = response.bodyAsText()

        return responseText
    } catch (e: Exception) {
        return "Error fetching ad: ${e.localizedMessage}"
    }
}

