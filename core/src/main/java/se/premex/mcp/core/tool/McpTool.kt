package se.premex.mcp.core.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server

/**
 * Common interface for MCP tools that can be enabled/disabled in the application
 */
interface McpTool {
    /** Unique identifier for the tool */
    val id: String

    /** Display name of the tool (fallback when [nameRes] is 0) */
    val name: String

    /**
     * Android string resource for the localized display name; 0 = use [name].
     * Kept as a plain Int so this pure-JVM module needs no Android deps —
     * third-party tools (mcp-provider SDK) can ignore it.
     */
    val nameRes: Int get() = 0

    /** Whether the tool is enabled by default */
    val enabledByDefault: Boolean

    /**
     * Android AppFunction identifiers published by this tool module.
     *
     * The application keeps these functions in sync with the same user-facing
     * switch that controls MCP exposure. Tools that are not meaningful as
     * outbound AppFunctions leave this empty.
     */
    val appFunctionIds: Set<String> get() = emptySet()

    /** Consent text shown before enabling (fallback when [disclaimRes] is 0) */
    val disclaim: String?

    /** Android string resource for the localized consent text; 0 = use [disclaim]. */
    val disclaimRes: Int get() = 0

    /**
     * Configures the tool with the given server instance
     * @param server The MCP server instance to configure the tool with
     */
    fun configure(server: Server)

    fun requiredPermissions(): Set<String>
}
