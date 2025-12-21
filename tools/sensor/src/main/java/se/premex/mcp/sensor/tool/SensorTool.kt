package se.premex.mcp.sensor.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.sensor.configurator.SensorToolConfiguratorImpl

class SensorTool(
    val censorToolConfigurator: SensorToolConfiguratorImpl
) : McpTool {
    override val id: String = "sensor"
    override val name: String = "Sensors"
    override val enabledByDefault: Boolean = true
    override val disclaim: String? = null

    override fun configure(server: Server) {
        censorToolConfigurator.configure(server)
    }

    override fun requiredPermissions(): Set<String> {
        return setOf()
    }
}