package se.premex.mcp.screenshot.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.screenshot.configurator.ScreenshotToolConfiguratorImpl

class ScreenshotTool(
    val screenshotToolConfigurator: ScreenshotToolConfiguratorImpl
) : McpTool {
    override val id: String = "screenshot"
    override val name: String = "Screenshot"
    override val enabledByDefault: Boolean = true
    override val disclaim: String? = null

    override fun configure(server: Server) {
        screenshotToolConfigurator.configure(server)
    }

    override fun requiredPermissions(): Set<String> {
        // Note: MediaProjection permission is handled separately through an Intent
        // This doesn't work with standard Android permission system
        return setOf()
    }
}
