package se.premex.mcp.location.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server

interface LocationToolConfigurator {
    fun configure(server: Server)
}
