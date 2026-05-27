package se.premex.mcp.appfunctions.repositories

import se.premex.mcp.core.tool.AppFunctionParameterSpec

/**
 * One AppFunction discovered on the device, projected into a stable internal shape.
 *
 * Built by AppFunctionsConfiguratorImpl from the platform's AppFunctionMetadata
 * returned by AppFunctionManager. The mcpToolName is the sanitized name used
 * when registering the function as an MCP tool on the SSE server.
 */
data class AppFunctionMetadataInfo(
    val packageName: String,
    val functionId: String,
    val mcpToolName: String,
    val description: String,
    val parameters: List<AppFunctionParameterSpec>,
) {
    companion object {
        /**
         * Builds the MCP tool name used to expose this function over SSE.
         * Sanitizes the package + function identifier into a single MCP-safe token.
         */
        fun mcpToolNameFor(packageName: String, functionId: String): String {
            val sanitized = "appfn__${packageName}__${functionId}"
                .replace(Regex("[^A-Za-z0-9_]"), "_")
            return sanitized
        }
    }
}
