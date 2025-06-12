package se.premex.mcp.sensor.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server

interface SensorToolConfigurator {
    fun configure(server: Server)
}