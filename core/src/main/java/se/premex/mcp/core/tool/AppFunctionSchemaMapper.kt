package se.premex.mcp.core.tool

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Converts a list of AppFunction parameter specs into an MCP ToolSchema.
 *
 * Pure JVM, no Android dependencies. Used by tools/appfunctions to project
 * platform AppFunction metadata into the MCP tool registration format.
 */
object AppFunctionSchemaMapper {

    fun toMcpToolSchema(parameters: List<AppFunctionParameterSpec>): ToolSchema {
        val properties = buildJsonObject {
            parameters.forEach { param ->
                put(param.name, buildJsonObject {
                    when (param.type) {
                        AppFunctionParameterSpec.ParameterType.STRING ->
                            put("type", JsonPrimitive("string"))
                        AppFunctionParameterSpec.ParameterType.INTEGER,
                        AppFunctionParameterSpec.ParameterType.LONG ->
                            put("type", JsonPrimitive("integer"))
                        AppFunctionParameterSpec.ParameterType.BOOLEAN ->
                            put("type", JsonPrimitive("boolean"))
                        AppFunctionParameterSpec.ParameterType.NUMBER ->
                            put("type", JsonPrimitive("number"))
                        AppFunctionParameterSpec.ParameterType.STRING_ARRAY -> {
                            put("type", JsonPrimitive("array"))
                            put("items", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            })
                        }
                    }
                    if (param.description.isNotEmpty()) {
                        put("description", JsonPrimitive(param.description))
                    }
                })
            }
        }

        val required = parameters.filter { it.required }.map { it.name }

        return ToolSchema(properties = properties, required = required)
    }
}
