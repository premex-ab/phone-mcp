package se.premex.mcp.core.tool

/**
 * Generic, Android-free description of one parameter on a discovered AppFunction.
 *
 * Produced by the tools/appfunctions configurator from the platform's
 * androidx.appfunctions parameter metadata, then handed to AppFunctionSchemaMapper
 * to project into an MCP ToolSchema.
 */
data class AppFunctionParameterSpec(
    val name: String,
    val type: ParameterType,
    val description: String?,
    val required: Boolean,
) {
    /**
     * Subset of AppFunctions parameter types supported by the Phase 1 bridge.
     * Functions with parameters outside this set are skipped during discovery
     * (logged at WARN by the configurator).
     */
    enum class ParameterType { STRING, INTEGER, LONG, BOOLEAN, NUMBER, STRING_ARRAY }
}
