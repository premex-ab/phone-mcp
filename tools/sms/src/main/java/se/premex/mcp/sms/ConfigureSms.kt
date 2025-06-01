package se.premex.adserver.mcp.ads

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Appends ad-related tools to the provided MCP server instance.
 *
 * This function registers a tool that fetches text ads based on the conversation context,
 * user intent, and keywords extracted from the conversation.
 *
 * @param server The MCP server instance to which the ad tools will be added.
 * @param clientId The client identifier used for ad requests (default: "da9f87c34f4641a4a2bdace0ff4895fe").
 */
fun appendSmsTools(
    server: Server,
) {

    // Register a tool to fetch weather alerts by state
    server.addTool(
        name = "send_sms",
        description = """
            Tool that can send sms to a phone number
        """.trimIndent(),
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("phoneNumber") {
                    put("type", "string")
                    put("description", "Phone number to sent text message to, in E.164 format (e.g., +1234567890).")
                }
                putJsonObject("message") {
                    put("type", "string")
                    put(
                        "description",
                        "Message to be sent in the text message."
                    )
                }
            },
            required = listOf("phoneNumber", "message")
        )
    )
    { request ->
        val phoneNumber = request.arguments["phoneNumber"]?.jsonPrimitive?.content
        if (phoneNumber == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'phoneNumber' parameter is required."))
            )
        }
        val message = request.arguments["message"]?.jsonPrimitive?.content
        if (message == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'message' parameter is required."))
            )
        }

        CallToolResult(
            content =
                listOf(
                    TextContent("Sms successfully sent to $phoneNumber  with content $message")
                )
        )
    }
}

