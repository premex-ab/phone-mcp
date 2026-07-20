package se.premex.mcp.location.tool

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.location.configurator.LocationToolConfiguratorImpl

class LocationTool(
    val locationToolConfigurator: LocationToolConfiguratorImpl
) : McpTool {
    override val id: String = "location"
    override val name: String = "Location"
    override val enabledByDefault: Boolean = false
    override val disclaim: String?
        get() = "PRIVACY WARNING: Enabling location access\n\n" +
                "By enabling this tool, you grant this application and any connected AI services permission to:\n" +
                "• Read your phone's current geographic position (GPS/network)\n\n" +
                "You acknowledge that:\n" +
                "• Connected AI services may process your location according to their privacy policies\n" +
                "• You can revoke access at any time by disabling this tool\n\n" +
                "We do not store your location, but connected AI services may."

    override fun configure(server: Server) {
        locationToolConfigurator.configure(server)
    }

    override fun requiredPermissions(): Set<String> {
        return setOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}
