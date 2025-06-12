package se.premex.mcp.camera.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server

interface CameraToolConfigurator {
    fun configure(server: Server)
}
