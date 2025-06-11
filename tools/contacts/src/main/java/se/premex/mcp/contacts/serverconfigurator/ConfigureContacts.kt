package se.premex.mcp.contacts.serverconfigurator

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import se.premex.mcp.contacts.repositories.ContactPhoneInfo
import se.premex.mcp.contacts.repositories.ContactsRepository


internal fun appendContactsTools(
    server: Server,
    contactsRepository: ContactsRepository,
) {

    // Register a tool to fetch weather alerts by state
    server.addTool(
        name = "get_phone_contacts",
        description = """
            Retreive phone number(s) from contacts.
        """.trimIndent(),
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                    put(
                        "description",
                        "The name of the contact to search for"
                    )
                }
            },
            required = listOf("name")
        )
    )
    { request ->
        val phoneNumber = request.arguments["name"]?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(
                content = listOf(TextContent("The 'name' parameter is required."))
            )


        val success: List<ContactPhoneInfo> =
            try {
                contactsRepository.findPhoneNumberByName(phoneNumber)
            } catch (e: Exception) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("Error retrieving contacts: ${e.message}"))
                )
            }

        CallToolResult(
            content =
                success.map {
                    TextContent(
                        "Name: ${it.contactName}, Phone: ${it.phoneNumber}, Type: ${it.phoneType}"
                    )
                }
        )
    }
}
