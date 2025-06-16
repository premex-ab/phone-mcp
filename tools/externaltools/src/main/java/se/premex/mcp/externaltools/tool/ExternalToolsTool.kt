package se.premex.mcp.externaltools.tool

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
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
        return setOf("android.permission.READ_CONTENT_PROVIDER")
    }

    /**
     * Registers a management tool with the MCP server for tool discovery operations
     */
    private fun registerManagementTool(server: Server) {
        server.addTool(
            name = "external_tools_discovery",
            description = """
                Tool that allows discovery of MCP tools exposed by other applications via content providers.
                You can list all currently discovered external tools.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("action") {
                        put("type", "string")
                        put("description", "Action to perform: 'list' to show all currently discovered tools")
                    }
                },
                required = listOf("action")
            )
        ) { request ->
            val action = request.arguments["action"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("Missing required 'action' parameter"))
                )

            val result = when (action) {
                "list" -> {
                    val tools = externalToolsConfigurator.getRegisteredTools()
                    if (tools.isEmpty()) {
                        "No external tools are currently discovered"
                    } else {
                        val toolInfoLines = tools.joinToString("\n\n") { toolInfo ->
                            """
                            Tool: ${toolInfo.toolName}
                            Description: ${toolInfo.description}
                            Provider Authority: ${toolInfo.authority}
                            Required Parameters: ${toolInfo.requiredFields.joinToString(", ")}
                            """.trimIndent()
                        }
                        "Discovered external tools:\n\n$toolInfoLines"
                    }
                }
                else -> {
                    "Invalid action: $action. Valid actions are 'list'."
                }
            }

            CallToolResult(
                content = listOf(TextContent(result))
            )
        }
    }
}
