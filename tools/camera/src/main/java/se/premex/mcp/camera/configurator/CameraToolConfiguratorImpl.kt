package se.premex.mcp.camera.configurator

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.camera.repositories.CameraInfo
import se.premex.mcp.camera.repositories.CameraRepository

class CameraToolConfiguratorImpl(
    private val cameraRepository: CameraRepository,
) : CameraToolConfigurator {

    /**
     * Configures the MCP server with the camera tool.
     */
    override fun configure(server: Server) {
        server.addTool(
            name = "phone_camera",
            description = """
                Access device camera information and capabilities.
                This tool provides information about the available cameras on the device,
                including camera ID, facing direction, supported picture sizes, 
                video capabilities, and other hardware characteristics.
            """.trimIndent(),
        ) { request ->

            val camerasInfo: List<CameraInfo> =
                try {
                    cameraRepository.getCamerasInfo()
                } catch (e: Exception) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent("Error retrieving camera data: ${e.message}"))
                    )
                }

            if (camerasInfo.isEmpty()) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("No cameras found on this device."))
                )
            }

            CallToolResult(
                content = camerasInfo.map { camera ->
                    TextContent(camera.toString())
                }
            )
        }

        // TODO: Implement the photo taking functionality
        server.addTool(
            name = "phone_take_photo",
            description = """
                Take a photo using the android device camera.
            """.trimIndent(),
            inputSchema = Tool.Input(), //TODO: Define input schema with camera options if needed
        ) { request ->
            TODO("Implement photo taking functionality and return ImageContent(...)")
        }
    }
}
