package se.premex.adserver.mcp.ads

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import se.premex.mcp.sms.SmsSender

/**
 * Registers an SMS sending tool to a Model Context Protocol server.
 *
 * This function configures and adds a tool named "send_sms" to the provided MCP server,
 * which enables the capability to send SMS messages to specified phone numbers.
 *
 * @param server The MCP server instance to which the SMS tool will be added
 * @param smsSender The SMS sending service implementation that will handle the actual message delivery
 *
 * The tool expects two required parameters:
 * - phoneNumber: String in E.164 format (e.g., +1234567890)
 * - message: String content to be sent as the SMS message
 *
 * @return None. The function registers the tool with the server directly.
 */
fun appendSmsTools(
    server: Server,
    smsSender: SmsSender,
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
                    put(
                        "description",
                        "Phone number to sent text message to, in E.164 format (e.g., +1234567890)."
                    )
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
            ?: return@addTool CallToolResult(
                content = listOf(TextContent("The 'phoneNumber' parameter is required."))
            )

        val message = request.arguments["message"]?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(
                content = listOf(TextContent("The 'message' parameter is required."))
            )


        val success = smsSender.sendSms(phoneNumber, message)

        val result = if (success) {
            "Sms successfully sent to $phoneNumber with content $message"
        } else {
            "Failed to send SMS to $phoneNumber."
        }
        CallToolResult(
            content =
                listOf(
                    TextContent(result)
                )
        )
    }
}
