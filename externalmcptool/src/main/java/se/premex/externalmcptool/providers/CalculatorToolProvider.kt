package se.premex.externalmcptool.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject
import kotlin.math.pow

/**
 * A ContentProvider that implements the MCP Tool protocol for a simple calculator tool.
 * This tool can perform basic arithmetic operations like addition, subtraction, multiplication, and division.
 */
class CalculatorToolProvider : ContentProvider() {
    companion object {
        // MCP Tool protocol method names
        const val METHOD_GET_TOOL_INFO = "get_tool_info"
        const val METHOD_EXECUTE_TOOL = "execute_tool"

        // Bundle keys
        const val KEY_TOOL_NAME = "tool_name"
        const val KEY_TOOL_DESCRIPTION = "tool_description"
        const val KEY_TOOL_INPUT_SCHEMA = "tool_input_schema"
        const val KEY_TOOL_INPUT_REQUIRED = "tool_input_required"
        const val KEY_TOOL_ARGUMENTS = "tool_arguments"
        const val KEY_TOOL_RESULT = "tool_result"
        const val KEY_SUCCESS = "success"
        const val KEY_ERROR_MESSAGE = "error_message"
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        return when (method) {
            METHOD_GET_TOOL_INFO -> getToolInfo()
            METHOD_EXECUTE_TOOL -> executeToolRequest(extras)
            else -> errorBundle("Unknown method: $method")
        }
    }

    private fun getToolInfo(): Bundle {
        return Bundle().apply {
            putBoolean(KEY_SUCCESS, true)
            putString(KEY_TOOL_NAME, "calculator")
            putString(KEY_TOOL_DESCRIPTION, "A simple calculator tool that can perform basic arithmetic operations")

            // JSON schema for the tool inputs
            putString(KEY_TOOL_INPUT_SCHEMA, """
                {
                    "operation": {
                        "type": "string", 
                        "description": "The operation to perform (add, subtract, multiply, divide, power)"
                    },
                    "a": {
                        "type": "number", 
                        "description": "First operand"
                    },
                    "b": {
                        "type": "number", 
                        "description": "Second operand"
                    }
                }
            """.trimIndent())

            // All parameters are required for this tool
            putStringArray(KEY_TOOL_INPUT_REQUIRED, arrayOf("operation", "a", "b"))
        }
    }

    private fun executeToolRequest(extras: Bundle?): Bundle {
        if (extras == null) return errorBundle("No extras provided")

        val toolName = extras.getString(KEY_TOOL_NAME)
            ?: return errorBundle("Tool name not specified")

        @Suppress("UNCHECKED_CAST")
        val arguments = extras.getSerializable(KEY_TOOL_ARGUMENTS) as? Map<String, Any>
            ?: return errorBundle("Invalid arguments")

        // Extract parameters
        val operation = arguments["operation"]?.toString() ?: return errorBundle("Operation is required")
        val a = arguments["a"]?.toString()?.toDoubleOrNull() ?: return errorBundle("Invalid first operand")
        val b = arguments["b"]?.toString()?.toDoubleOrNull() ?: return errorBundle("Invalid second operand")

        // Execute calculation
        val result = try {
            when (operation.lowercase()) {
                "add" -> a + b
                "subtract" -> a - b
                "multiply" -> a * b
                "divide" -> {
                    if (b == 0.0) throw ArithmeticException("Division by zero")
                    a / b
                }
                "power" -> a.pow(b)
                else -> return errorBundle("Unknown operation: $operation")
            }
        } catch (e: Exception) {
            return errorBundle("Calculation error: ${e.message}")
        }

        // Format result as JSON
        val resultJson = JSONObject().apply {
            put("result", result)
            put("operation", operation)
            put("a", a)
            put("b", b)
        }.toString()

        return Bundle().apply {
            putBoolean(KEY_SUCCESS, true)
            putString(KEY_TOOL_RESULT, resultJson)
        }
    }

    private fun errorBundle(message: String): Bundle {
        return Bundle().apply {
            putBoolean(KEY_SUCCESS, false)
            putString(KEY_ERROR_MESSAGE, message)
        }
    }

    // Required ContentProvider implementations
    override fun onCreate(): Boolean = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = "application/vnd.mcp.tool"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
