package se.premex.mcp.input.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server

interface InputToolConfigurator {
    /**
     * Configures the Input tool with the given server
     * @param server The MCP server to configure the tool with
     */
    fun configure(server: Server)
}
