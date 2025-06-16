package se.premex.mcp.externaltools.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server
import se.premex.mcp.externaltools.repositories.ExternalToolInfo

/**
 * Interface defining how external tools are discovered and configured with the MCP server.
 */
interface ExternalToolsConfigurator {
    /**
     * Configure all discovered external tools with the MCP server
     * @param server The MCP server instance to configure the tools with
     */
    fun configureTools(server: Server)

    /**
     * Get complete information about all discovered tools
     * @return List of ExternalToolInfo objects containing all data needed to register tools with the server
     */
    fun getRegisteredTools(): List<ExternalToolInfo>
}
