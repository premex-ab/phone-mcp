package se.premex.mcp.camera.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.camera.configurator.CameraToolConfiguratorImpl

class CameraTool(
    val cameraToolConfigurator: CameraToolConfiguratorImpl
) : McpTool {
    override val id: String = "camera"
    override val name: String = "Camera"
    override val enabledByDefault: Boolean = true
    override val disclaim: String? = null

    override fun configure(server: Server) {
        cameraToolConfigurator.configure(server)
    }

    override fun requiredPermissions(): Set<String> {
        return setOf(android.Manifest.permission.CAMERA)
    }
}
