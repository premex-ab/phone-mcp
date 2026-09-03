package se.premex.mcp.appfunctions.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.appfunctions.configurator.AppFunctionsConfigurator
import se.premex.mcp.core.tool.McpTool
import javax.inject.Inject

/**
 * Bridges every AppFunction registered on the device (Android 16+) into the MCP
 * server as individual tools. Off by default; turning this on exposes a broad
 * surface to the connected MCP client over the bearer-token-authenticated SSE
 * channel.
 */
class AppFunctionsTool @Inject constructor(
    private val configurator: AppFunctionsConfigurator,
) : McpTool {
    override val id: String = "app_functions"
    override val name: String = "AppFunctions"
    override val enabledByDefault: Boolean = false
    override val disclaim: String? =
        "Exposes every AppFunction registered on this device (Android 16+) to the " +
            "connected MCP client. The set of exposed functions depends on which apps " +
            "are installed and what they publish. Bearer-token authentication on the " +
            "SSE server is the only gate."

    override fun configure(server: Server) {
        configurator.configureTools(server)
    }

    override fun requiredPermissions(): Set<String> = emptySet()
}
