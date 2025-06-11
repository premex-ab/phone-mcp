package se.premex.mcp.sensor.serverconfigurator

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.sensor.repositories.SensorInfo
import se.premex.mcp.sensor.repositories.SensorRepository


internal fun appendSensorTools(
    server: Server,
    contactsRepository: SensorRepository,
) {

    server.addTool(
        name = "phone_sensor",
        description = """
             These sensors are capable of providing raw data with high precision and accuracy, and are useful if you want to monitor three-dimensional device movement or positioning, or you want to monitor changes in the ambient environment near a device. For example, a game might track readings from a device's gravity sensor to infer complex user gestures and motions, such as tilt, shake, rotation, or swing. Likewise, a weather application might use a device's temperature sensor and humidity sensor to calculate and report the dewpoint, or a travel application might use the geomagnetic field sensor and accelerometer to report a compass bearing.
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
