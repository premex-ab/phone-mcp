package se.premex.mcp.input.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.input.configurator.InputToolConfigurator
import javax.inject.Inject

class InputTool @Inject constructor(
    private val configurator: InputToolConfigurator
) : McpTool {
    override val id: String = "input"
    override val name: String = "Input Control"
    override val enabledByDefault: Boolean = true
    override val disclaim: String? = "This tool allows AIs to simulate touch inputs on your device"

    override fun configure(server: Server) {
        configurator.configure(server)
    }

    override fun requiredPermissions(): Set<String> {
        return setOf(android.Manifest.permission.SYSTEM_ALERT_WINDOW)
    }
}
