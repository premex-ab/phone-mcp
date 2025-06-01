package se.premex.mcp.core.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server

/**
 * Common interface for MCP tools that can be enabled/disabled in the application
 */
interface McpTool {
    /** Unique identifier for the tool */
    val id: String

    /** Display name of the tool */
    val name: String

    /** Whether the tool is enabled by default */
    val enabledByDefault: Boolean

    /**
     * Configures the tool with the given server instance
     * @param server The MCP server instance to configure the tool with
     */
    fun configure(server: Server)
}
