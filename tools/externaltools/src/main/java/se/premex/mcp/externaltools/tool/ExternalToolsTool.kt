package se.premex.mcp.externaltools.tool

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.externaltools.configurator.ExternalToolsConfigurator
import javax.inject.Inject

private const val TAG = "ExternalToolsTool"

/**
 * Implementation of McpTool that discovers external tools from other applications
 * via content providers and registers each tool with the MCP server
 */
class ExternalToolsTool @Inject constructor(
    private val externalToolsConfigurator: ExternalToolsConfigurator
) : McpTool {
    override val id = "external_tools"
    override val name = "External Tools"
    override val enabledByDefault = true
    override val disclaim: String? = null

    override fun configure(server: Server) {
        Log.d(TAG, "Configuring external tools")

        // Configure all discovered external tools with the MCP server
        // This will call server.addTool() for each discovered tool
        externalToolsConfigurator.configureTools(server)

        // Register a management tool for discovery operations
        registerManagementTool(server)
    }

    override fun requiredPermissions(): Set<String> {
        return setOf()
    }

    /**
     * Registers a management tool with the MCP server for tool discovery operations
     */
    private fun registerManagementTool(server: Server) {
        val tools = externalToolsConfigurator.getRegisteredTools()
        tools.forEach { toolInfo ->
            server.addTool(
                name = toolInfo.toolName,
                description = toolInfo.description,
                inputSchema = parseInputSchema(toolInfo.inputSchemaJson, toolInfo.requiredFields),
            ) { request ->
                Log.d(TAG, "Handling request for management tool: ${toolInfo.toolName}")

                val result: String = try {
                    val response = externalToolsConfigurator.handleExternalToolRequest(
                        authority = toolInfo.authority,
                        toolName = toolInfo.toolName,
                        arguments = request.arguments ?: emptyMap()
                    )


                    response
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing external tool ${toolInfo.toolName}", e)
                    "Error executing tool: ${e.message ?: "Unknown error"}"
                }

                CallToolResult(
                    content = listOf(
                        TextContent(result)
                    )
                )
            }
        }
    }

    /**
     * Parse the input schema JSON string into a Tool.Input object
     */
    private fun parseInputSchema(schema: String, required: List<String>) = ToolSchema(
        properties = Json.decodeFromString<JsonObject>(schema),
        required = required
    )
}
