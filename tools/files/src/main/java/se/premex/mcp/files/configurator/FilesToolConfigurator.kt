package se.premex.mcp.files.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server

interface FilesToolConfigurator {
    fun configure(server: Server)
}
