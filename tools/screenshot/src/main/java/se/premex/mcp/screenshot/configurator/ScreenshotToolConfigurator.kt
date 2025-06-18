package se.premex.mcp.screenshot.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server

interface ScreenshotToolConfigurator {
    fun configure(server: Server)
}
