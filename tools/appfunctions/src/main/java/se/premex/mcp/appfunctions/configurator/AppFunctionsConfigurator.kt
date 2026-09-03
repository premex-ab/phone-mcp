package se.premex.mcp.appfunctions.configurator

import io.modelcontextprotocol.kotlin.sdk.server.Server

/**
 * Discovers AppFunctions registered on the device and configures each as an
 * MCP tool on the given server.
 *
 * On Android < 16 (API < 36) or when the AppFunctions service is unavailable,
 * implementations log and register no tools rather than throw.
 */
interface AppFunctionsConfigurator {
    fun configureTools(server: Server)
}
