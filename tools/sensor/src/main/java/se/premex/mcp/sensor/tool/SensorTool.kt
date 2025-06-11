package se.premex.mcp.sensor.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.sensor.repositories.SensorRepository
import se.premex.mcp.sensor.serverconfigurator.appendSensorTools
import se.premex.mcp.core.tool.McpTool

class SensorTool(val sensorRepository: SensorRepository) : McpTool {
    override val id: String = "sensor"
    override val name: String = "Sensors"
    override val enabledByDefault: Boolean = true
    override val disclaim: String? = null

    override fun configure(server: Server) {
        appendSensorTools(server, sensorRepository)
    }

    override fun requiredPermissions(): Set<String> {
        return setOf()
    }
}