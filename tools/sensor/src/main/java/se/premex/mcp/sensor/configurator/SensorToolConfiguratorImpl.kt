package se.premex.mcp.sensor.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import se.premex.mcp.sensor.repositories.SensorInfo
import se.premex.mcp.sensor.repositories.SensorRepository


class SensorToolConfiguratorImpl(
    private val contactsRepository: SensorRepository,
) : SensorToolConfigurator {

    /**
     * Configures the MCP server with the sensor tool.
     */
    override fun configure(server: Server) {
        server.addTool(
            name = "phone_sensor",
            description = """
           Access all Android sensors. 
           Some of these sensors are hardware-based and some are software-based. 
           Hardware-based sensors are physical components built into a handset 
           or tablet device. They derive their data by directly measuring 
           specific environmental properties, such as acceleration, 
           geomagnetic field strength, or angular change. 
           Software-based sensors are not physical devices, 
           although they mimic hardware-based sensors. 
           Software-based sensors derive their data from one or more of the
           hardware-based sensors and are sometimes called virtual 
           sensors or synthetic sensors. The linear acceleration sensor 
           and the gravity sensor are examples of software-based sensors. 
       """.trimIndent(),
        )
        { request ->

            val success: List<SensorInfo> =
                try {
                    contactsRepository.getStatus()
                } catch (e: Exception) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent("Error retrieving sensor data "))
                    )
                }

            CallToolResult(
                content =
                    success.map { s ->
                        TextContent(
                            s.toString()
                        )
                    }
            )
        }
    }
}