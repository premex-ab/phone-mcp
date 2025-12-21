package se.premex.mcp.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class ToolInput {
    @Serializable
    data class StringInput(
        val name: String,
        val description: String,
        val required: Boolean
    ) : ToolInput()
}

@Serializable
data class ToolInfo(val description: String, val inputs: List<ToolInput>)

@Serializable
data class Tools(
    val tools: Map<String, ToolInfo>
)
/**
 * A ContentProvider that implements the MCP Tool protocol for a simple calculator tool.
 * This tool can perform basic arithmetic operations like addition, subtraction, multiplication, and division.
 */
abstract class McpProvider : ContentProvider() {
    companion object {
        // MCP Tool protocol method names
        const val METHOD_GET_TOOL_INFO = "get_tool_info"
        const val METHOD_EXECUTE_TOOL = "execute_tool"

        // Bundle keys
        const val KEY_TOOL = "tool"
        const val KEY_TOOL_NAME = "tool_name"
        const val KEY_TOOL_ARGUMENTS = "tool_arguments"
        const val KEY_TOOL_RESULT = "tool_result"
        const val KEY_SUCCESS = "success"
        const val KEY_ERROR_MESSAGE = "error_message"
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        return when (method) {
            METHOD_GET_TOOL_INFO -> getToolInfoAsBundle(getToolInfo())
            METHOD_EXECUTE_TOOL -> executeToolRequest(extras)
            else -> errorBundle("Unknown method: $method")
        }
    }

    abstract fun getToolInfo(): Tools

    private fun getToolInfoAsBundle(tools: Tools): Bundle {
        return Bundle().apply {
            putBoolean(KEY_SUCCESS, true)
            putString(KEY_TOOL, Json.encodeToString(Tools.serializer(), tools))
        }
    }

    fun executeToolRequest(extras: Bundle?): Bundle {
        if (extras == null) return errorBundle("No extras provided")

        val toolName = extras.getString(KEY_TOOL_NAME)
            ?: return errorBundle("Tool name not specified")

        val arguments = extras.getSerializable(KEY_TOOL_ARGUMENTS) as? Map<String, Any>
            ?: return errorBundle("Invalid or missing tool arguments")

        return try {
            executeToolRequest(arguments, toolName)
        } catch (e: Exception) {
            errorBundle("Error executing tool: ${e.message}")
        }
    }

    abstract fun executeToolRequest(extras: Map<String, Any>, toolInfoName: String): Bundle

    fun errorBundle(message: String): Bundle {
        return Bundle().apply {
            putBoolean(KEY_SUCCESS, false)
            putString(KEY_ERROR_MESSAGE, message)
        }
    }

    // Required ContentProvider implementations
    override fun onCreate(): Boolean = true
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = "application/vnd.mcp.tool"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
