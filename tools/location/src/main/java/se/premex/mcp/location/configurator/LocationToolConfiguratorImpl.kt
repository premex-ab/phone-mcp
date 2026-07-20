package se.premex.mcp.location.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import se.premex.mcp.location.repositories.LocationRepository

class LocationToolConfiguratorImpl(
    private val locationRepository: LocationRepository,
) : LocationToolConfigurator {

    /**
     * Configures the MCP server with the location tool.
     */
    override fun configure(server: Server) {
        server.addTool(
            name = "phone_location",
            description = """
                Get the phone's current geographic location as latitude/longitude.
                Returns the freshest available position from GPS or network
                providers, including accuracy, altitude, speed and timestamp
                when available. Falls back to the last known position if a
                fresh fix cannot be obtained within a few seconds.
            """.trimIndent(),
        ) { request ->
            try {
                val location = runBlocking { locationRepository.getCurrentLocation() }

                if (location == null) {
                    CallToolResult(
                        content = listOf(
                            TextContent(
                                "Location is currently unavailable. " +
                                        "Make sure location services are enabled on the phone."
                            )
                        )
                    )
                } else {
                    CallToolResult(content = listOf(TextContent(location.toString())))
                }
            } catch (e: SecurityException) {
                CallToolResult(
                    content = listOf(
                        TextContent(
                            "Location permission not granted. " +
                                    "Grant the location permission in the Phone MCP app and restart the server."
                        )
                    )
                )
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("Error retrieving location: ${e.message}"))
                )
            }
        }
    }
}
