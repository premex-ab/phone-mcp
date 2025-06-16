package se.premex.mcp.externaltools.configurator

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*
import se.premex.mcp.externaltools.repositories.ExternalToolInfo
import se.premex.mcp.externaltools.repositories.ExternalToolRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ExternalToolsConfigImpl"

/**
 * Implementation of ExternalToolsConfigurator that communicates with content providers to
 * register and configure external tools.
 */
@Singleton
class ExternalToolsConfiguratorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val externalToolRepository: ExternalToolRepository
) : ExternalToolsConfigurator {

    companion object {
        // Content provider method names
        const val METHOD_GET_TOOL_INFO = "get_tool_info"
        const val METHOD_EXECUTE_TOOL = "execute_tool"

        // Bundle keys for content provider communication
        const val KEY_TOOL_NAME = "tool_name"
        const val KEY_TOOL_DESCRIPTION = "tool_description"
        const val KEY_TOOL_INPUT_SCHEMA = "tool_input_schema"
        const val KEY_TOOL_INPUT_REQUIRED = "tool_input_required"
        const val KEY_TOOL_ARGUMENTS = "tool_arguments"
        const val KEY_TOOL_RESULT = "tool_result"
        const val KEY_SUCCESS = "success"
        const val KEY_ERROR_MESSAGE = "error_message"

        // MIME type for MCP tool content providers
        const val MIME_TYPE_MCP_TOOL = "application/vnd.mcp.tool"
    }

    override fun configureTools(server: Server) {
        // Discover all content providers with our custom MIME type
        val externalTools = discoverExternalTools()

        // For each discovered external tool, create a separate server.addTool() call
        for (toolInfo in externalTools) {
            try {
                // Register this specific tool with the server directly
                server.addTool(
                    name = toolInfo.toolName,
                    description = toolInfo.description,
                    inputSchema = createInputSchema(toolInfo)
                ) { request ->
                    // Handle the request by forwarding it to the content provider
                    val response = handleExternalToolRequest(toolInfo.authority, toolInfo.toolName, request.arguments)
                    CallToolResult(
                        content = listOf(TextContent(response))
                    )
                }

                Log.d(TAG, "Successfully registered external tool: ${toolInfo.toolName} from ${toolInfo.authority}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register tool from ${toolInfo.authority}", e)
            }
        }
    }

    /**
     * Discover all content providers with our custom MIME type
     */
    private fun discoverExternalTools(): List<ExternalToolInfo> {
        val tools = mutableListOf<ExternalToolInfo>()
        val packageManager = context.packageManager

        // Get all content providers registered on the device
        val resolveInfoList = packageManager.queryIntentContentProviders(
            Intent(Intent.ACTION_VIEW).setType(MIME_TYPE_MCP_TOOL), 0
        )

        for (resolveInfo in resolveInfoList) {
            val providerInfo = resolveInfo.providerInfo ?: continue
            val authority = providerInfo.authority ?: continue

            try {
                val toolInfo = getToolInfoFromProvider(authority)
                if (toolInfo != null) {
                    tools.add(toolInfo)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get tool info from provider: $authority", e)
            }
        }

        return tools
    }

    override fun getRegisteredTools(): List<ExternalToolInfo> {
        return discoverExternalTools()
    }

    private fun createInputSchema(toolInfo: ExternalToolInfo): Tool.Input {
        // Parse the stored schema into a JsonObject
        val jsonParser = Json { ignoreUnknownKeys = true }
        val properties = try {
            jsonParser.parseToJsonElement(toolInfo.inputSchemaJson).jsonObject
        } catch (e: Exception) {
            // If parsing fails, create an empty schema
            buildJsonObject {}
        }

        return Tool.Input(
            properties = properties,
            required = toolInfo.requiredFields
        )
    }

    private fun getToolInfoFromProvider(authority: String): ExternalToolInfo? {
        val contentResolver = context.contentResolver
        val uri = Uri.parse("content://$authority")

        val result = try {
            contentResolver.call(uri, METHOD_GET_TOOL_INFO, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling content provider $authority", e)
            null
        } ?: return null

        val success = result.getBoolean(KEY_SUCCESS, false)
        if (!success) {
            val errorMessage = result.getString(KEY_ERROR_MESSAGE)
            Log.e(TAG, "Provider returned error: $errorMessage")
            return null
        }

        val toolName = result.getString(KEY_TOOL_NAME) ?: return null
        val toolDescription = result.getString(KEY_TOOL_DESCRIPTION) ?: return null
        val inputSchemaString = result.getString(KEY_TOOL_INPUT_SCHEMA) ?: return null
        val requiredFieldsArray = result.getStringArray(KEY_TOOL_INPUT_REQUIRED) ?: return null

        return ExternalToolInfo(
            authority = authority,
            toolName = toolName,
            description = toolDescription,
            inputSchemaJson = inputSchemaString,
            requiredFields = requiredFieldsArray.toList(),
            mimeType = MIME_TYPE_MCP_TOOL
        )
    }

    private fun handleExternalToolRequest(
        authority: String,
        toolName: String,
        arguments: Map<String, JsonElement>
    ): String {
        val contentResolver = context.contentResolver
        val uri = Uri.parse("content://$authority")

        // Convert arguments to Bundle
        val args = Bundle().apply {
            putString(KEY_TOOL_NAME, toolName)

            // Convert JSON arguments to a format that can be passed through a Bundle
            val argsMap = mutableMapOf<String, String>()
            arguments.forEach { (key, value) ->
                when (value) {
                    is JsonPrimitive -> argsMap[key] = value.toString().removeSurrounding("\"")
                    else -> argsMap[key] = value.toString()
                }
            }
            putSerializable(KEY_TOOL_ARGUMENTS, argsMap as java.io.Serializable)
        }

        val result = try {
            contentResolver.call(uri, METHOD_EXECUTE_TOOL, null, args)
        } catch (e: Exception) {
            Bundle().apply {
                putBoolean(KEY_SUCCESS, false)
                putString(KEY_ERROR_MESSAGE, "Error calling content provider: ${e.message}")
            }
        } ?: Bundle().apply {
            putBoolean(KEY_SUCCESS, false)
            putString(KEY_ERROR_MESSAGE, "Content provider returned null")
        }

        val success = result.getBoolean(KEY_SUCCESS, false)
        return if (success) {
            result.getString(KEY_TOOL_RESULT) ?: "Tool executed successfully but no result was returned"
        } else {
            "Failed to execute tool: ${result.getString(KEY_ERROR_MESSAGE) ?: "Unknown error"}"
        }
    }
}
