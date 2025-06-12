package se.premex.mcp.camera.configurator

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import se.premex.mcp.camera.repositories.CameraInfo
import se.premex.mcp.camera.repositories.CameraRepository
import java.io.File
import java.util.Base64

class CameraToolConfiguratorImpl(
    private val cameraRepository: CameraRepository,
) : CameraToolConfigurator {

    /**
     * Configures the MCP server with the camera tool.
     */
    override fun configure(server: Server) {
        server.addTool(
            name = "phone_get_cameras",
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

        server.addTool(
            name = "phone_take_photo",
            description = """
                Take a photo using the device camera.
                You can specify which camera to use by providing a camera_id parameter 
                (obtained from phone_get_cameras tool). If no camera_id is provided, 
                the default back-facing camera will be used.
                Additional parameters allow fine control over camera settings:
                - quality: Image quality from 1-100 (default: 80)
                - flash_mode: Flash mode (OFF, SINGLE, TORCH)
                - focus_mode: Focus mode (AUTO, CONTINUOUS_PICTURE, CONTINUOUS_VIDEO, EDOF, FIXED, INFINITY, MACRO, MANUAL)
                - white_balance: White balance mode (AUTO, CLOUDY_DAYLIGHT, DAYLIGHT, FLUORESCENT, INCANDESCENT, SHADE, TWILIGHT, WARM_FLUORESCENT)
                - zoom_level: Digital zoom level (1.0 = no zoom, higher values = more zoom)
                - picture_size: Desired image resolution in format "WIDTHxHEIGHT" (e.g. "4032x3024")
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("camera_id") {
                        put("type", "string")
                        put(
                            "description",
                            "Optional camera ID to use for taking the photo (from phone_get_cameras tool)"
                        )
                    }
                    putJsonObject("quality") {
                        put("type", "integer")
                        put(
                            "description",
                            "Optional photo quality from 1-100 (default: 80)"
                        )
                    }
                    putJsonObject("flash_mode") {
                        put("type", "string")
                        put(
                            "description",
                            "Optional flash mode: OFF, SINGLE, TORCH (default: camera's auto flash)"
                        )
                    }
                    putJsonObject("focus_mode") {
                        put("type", "string")
                        put(
                            "description",
                            "Optional focus mode: AUTO, CONTINUOUS_PICTURE, CONTINUOUS_VIDEO, EDOF, FIXED, INFINITY, MACRO, MANUAL (default: CONTINUOUS_PICTURE)"
                        )
                    }
                    putJsonObject("white_balance") {
                        put("type", "string")
                        put(
                            "description",
                            "Optional white balance mode: AUTO, CLOUDY_DAYLIGHT, DAYLIGHT, FLUORESCENT, INCANDESCENT, SHADE, TWILIGHT, WARM_FLUORESCENT (default: AUTO)"
                        )
                    }
                    putJsonObject("zoom_level") {
                        put("type", "number")
                        put(
                            "description",
                            "Optional digital zoom level: 1.0 = no zoom, higher values = more zoom (limited by camera)"
                        )
                    }
                    putJsonObject("picture_size") {
                        put("type", "string")
                        put(
                            "description",
                            "Optional picture size in format 'WIDTHxHEIGHT' (e.g. '4032x3024'). Use values from phone_get_cameras"
                        )
                    }
                }
            )
        ) { request ->
            val cameraId = request.arguments["camera_id"]?.jsonPrimitive?.content
            val quality = request.arguments["quality"]?.jsonPrimitive?.content?.toIntOrNull() ?: 80
            val flashMode = request.arguments["flash_mode"]?.jsonPrimitive?.content
            val focusMode = request.arguments["focus_mode"]?.jsonPrimitive?.content
            val whiteBalance = request.arguments["white_balance"]?.jsonPrimitive?.content
            val zoomLevel = request.arguments["zoom_level"]?.jsonPrimitive?.content?.toFloatOrNull()
            val pictureSize = request.arguments["picture_size"]?.jsonPrimitive?.content

            try {
                // Quality should be between 1-100
                val qualityValidated = when {
                    quality < 1 -> 1
                    quality > 100 -> 100
                    else -> quality
                }

                // Take photo using the repository with all the parameters
                val photoFile: File? = runBlocking {
                    cameraRepository.takePhoto(
                        cameraId = cameraId,
                        quality = qualityValidated,
                        flashMode = flashMode,
                        focusMode = focusMode,
                        whiteBalance = whiteBalance,
                        zoomLevel = zoomLevel,
                        pictureSize = pictureSize
                    )
                }

                if (photoFile == null || !photoFile.exists()) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent("Failed to capture photo. Please try again."))
                    )
                }

                // Read the photo file as bytes
                val photoBytes = photoFile.readBytes()

                // Clean up the temporary file
                photoFile.delete()

                return@addTool CallToolResult(
                    content = listOf(
                        ImageContent(
                            data = Base64.getEncoder().encodeToString(photoBytes),
                            mimeType = "image/jpeg"
                        ),
                        TextContent("Photo captured successfully.")
                    )
                )
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(
                        TextContent("Error taking photo: ${e.message}")
                    )
                )
            }
        }
    }
}
