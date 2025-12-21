# MCP Provider

This module provides the foundation for creating Model Context Protocol (MCP) tools that can be used by AI assistants through an SSE MCP server.

## Overview

The MCP Provider module enables developers to create custom tools that can be discovered and used by AI assistants through a standardized protocol. These tools can be implemented in any Android application and will be discoverable by the MCP server.

## Integration Guide

Follow these steps to integrate the MCP Provider into your custom Android application:

### 1. Add Dependency

First, add the MCP Provider module as a dependency in your app's `build.gradle.kts` file:

```kotlin
dependencies {
    implementation(project(":mcp-provider"))
}
```

### 2. Create a Custom MCP Provider

Create a class that extends `McpProvider` and implements the required methods:

```kotlin
package com.example.yourapp

import android.os.Bundle
import org.json.JSONObject
import se.premex.mcp.provider.McpProvider
import se.premex.mcp.provider.ToolInfo
import se.premex.mcp.provider.ToolInput
import se.premex.mcp.provider.Tools

class MyMcpProvider : McpProvider() {
    override fun getToolInfo(): Tools {
        return Tools(
            mapOf(
                "your_tool_name" to ToolInfo(
                    description = "Description of your tool",
                    inputs = listOf(
                        ToolInput.StringInput(
                            name = "input_name",
                            description = "Description of the input",
                            required = true
                        )
                        // Add more inputs as needed
                    )
                )
                // Add more tools as needed
            )
        )
    }

    override fun executeToolRequest(arguments: Map<String, Any>, toolName: String): Bundle {
        return when(toolName) {
            "your_tool_name" -> {
                // Process the arguments and return results
                val input = arguments["input_name"]?.toString() ?: return errorBundle("Input is required")
                
                // Process input and produce result
                val result = // Your logic here
                
                Bundle().apply {
                    putBoolean(KEY_SUCCESS, true)
                    putString(KEY_TOOL_RESULT, result)
                }
            }
            else -> errorBundle("Unknown tool: $toolName")
        }
    }
}
```

### 3. Register Provider in AndroidManifest.xml

Add your provider to your application's `AndroidManifest.xml`:

```xml
<provider
    android:name=".MyMcpProvider"
    android:authorities="${applicationId}.authorities.McpProvider"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="se.premex.mcp.MCP_PROVIDER" />
    </intent-filter>
</provider>
```

The `authorities` attribute should follow the pattern `${applicationId}.authorities.McpProvider` to ensure uniqueness across different apps.

## Example Implementation

Here's a complete example of a custom provider that implements a calculator and text reversal tool:

```kotlin
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
```

## How It Works

1. The MCP Server scans for all applications that have declared a content provider with the intent filter `se.premex.mcp.MCP_PROVIDER`.
2. For each provider found, the server calls the `get_tool_info` method to retrieve information about available tools.
3. When an AI wants to use a tool, the server calls the `execute_tool` method with the appropriate arguments.
4. Your implementation processes the request and returns the result.

## Best Practices

- Provide clear and detailed descriptions for your tools and inputs.
- Handle all errors gracefully and return appropriate error messages.
- For complex operations, consider using background processing to avoid blocking the main thread.
- Test your provider thoroughly before publishing your app.
- Ensure your tool names are unique and descriptive.

## Protocol Reference

### Constants

- `METHOD_GET_TOOL_INFO`: Method name for retrieving tool information
- `METHOD_EXECUTE_TOOL`: Method name for executing a tool
- `KEY_TOOL`: Bundle key for tool data
- `KEY_TOOL_NAME`: Bundle key for tool name
- `KEY_TOOL_ARGUMENTS`: Bundle key for tool arguments
- `KEY_TOOL_RESULT`: Bundle key for tool result
- `KEY_SUCCESS`: Bundle key for operation success flag
- `KEY_ERROR_MESSAGE`: Bundle key for error messages

### Data Classes

- `ToolInput`: Defines inputs required by your tool
- `ToolInfo`: Contains description and inputs for a tool
- `Tools`: A collection of tools provided by your implementation
