package se.premex.externalmcptool

import android.os.Bundle
import org.json.JSONObject
import se.premex.mcp.provider.McpProvider
import se.premex.mcp.provider.ToolInfo
import se.premex.mcp.provider.ToolInput
import se.premex.mcp.provider.Tools
import kotlin.math.pow


class MyMcpProvider : McpProvider() {
    override fun getToolInfo(): Tools {
        return Tools(
            mapOf(
                "calculator" to ToolInfo(
                    description = "A simple calculator tool that can perform basic arithmetic operations",
                    inputs = listOf(
                        ToolInput.StringInput(
                            name = "operation",
                            description = "The operation to perform (add, subtract, multiply, divide, power)",
                            required = true
                        ),
                        ToolInput.StringInput(
                            name = "a",
                            description = "First operand",
                            required = true
                        ),
                        ToolInput.StringInput(
                            name = "b",
                            description = "Second operand",
                            required = true
                        )
                    )
                ),
                "reverse" to ToolInfo(
                    description = "A simple tool that can reverse a string",
                    inputs = listOf(
                        ToolInput.StringInput(
                            name = "text",
                            description = "The text to reverse",
                            required = true
                        )
                    )
                )
            )
        )
    }

    override fun executeToolRequest(arguments: Map<String, Any>, toolName: String): Bundle {
        return when(toolName) {
            "calculator" -> return executeOperation(arguments)
            "reverse" -> return executeReverse(arguments)
            else -> return errorBundle("Unknown tool: $toolName")
        }
    }

    private fun executeReverse(arguments: Map<String, Any>): Bundle {
        val text =
            arguments["text"]?.toString() ?: return errorBundle("text is required")

        return Bundle().apply {
            putBoolean(KEY_SUCCESS, true)
            putString(KEY_TOOL_RESULT, text.reversed())
        }

    }
    private fun executeOperation(arguments: Map<String, Any>): Bundle {

        // Extract parameters
        val operation =
            arguments["operation"]?.toString() ?: return errorBundle("Operation is required")
        val a = arguments["a"]?.toString()?.toDoubleOrNull()
            ?: return errorBundle("Invalid first operand")
        val b = arguments["b"]?.toString()?.toDoubleOrNull()
            ?: return errorBundle("Invalid second operand")

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
}