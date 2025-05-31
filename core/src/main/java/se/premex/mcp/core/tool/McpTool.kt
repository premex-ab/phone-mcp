package se.premex.mcp.core.tool

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
}
