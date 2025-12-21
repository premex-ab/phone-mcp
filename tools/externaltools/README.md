# MCP External Tools Integration Guide

This guide explains how third-party Android applications can expose tools to the MCP (Model Context Protocol) server through content providers.

## Overview

MCP allows AI models to interact with device capabilities through tools. With the External Tools module, any Android app can expose its functionality as MCP tools without direct integration into the main MCP app. This is achieved through Android's Content Provider API and a standardized protocol.

## How It Works

1. Your app exposes a content provider that implements the MCP Tool protocol
2. The MCP app automatically discovers your tool through its MIME type
3. The MCP server registers your tool, making it available to AI models
4. When an AI invokes your tool, the MCP app forwards the request to your content provider
5. Your content provider executes the request and returns the result

## Implementation Steps

### 1. Add a Content Provider to Your App

Create a content provider class in your application:

```kotlin
class McpToolProvider : ContentProvider() {
    companion object {
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
            putString(KEY_TOOL_NAME, "my_tool_name")
            putString(KEY_TOOL_DESCRIPTION, "Description of what my tool does")
            
            // JSON schema for the tool inputs
            // This should be a JSON object string with properties
            putString(KEY_TOOL_INPUT_SCHEMA, """
                {
                    "param1": {"type": "string", "description": "Description of param1"},
                    "param2": {"type": "number", "description": "Description of param2"}
                }
            """.trimIndent())
            
            // Array of required parameter names
            putStringArray(KEY_TOOL_INPUT_REQUIRED, arrayOf("param1"))
        }
    }
    
    private fun executeToolRequest(extras: Bundle?): Bundle {
        if (extras == null) return errorBundle("No extras provided")
        
        val toolName = extras.getString(KEY_TOOL_NAME)
            ?: return errorBundle("Tool name not specified")
            
        @Suppress("UNCHECKED_CAST")
        val arguments = extras.getSerializable(KEY_TOOL_ARGUMENTS) as? Map<String, String>
            ?: return errorBundle("Invalid arguments")
        
        // Execute your tool's logic here
        val result = try {
            // Replace with your actual implementation
            "Your tool result: processed ${arguments["param1"]}"
        } catch (e: Exception) {
            return errorBundle("Execution error: ${e.message}")
        }
        
        return Bundle().apply {
            putBoolean(KEY_SUCCESS, true)
            putString(KEY_TOOL_RESULT, result)
        }
    }
    
    private fun errorBundle(message: String): Bundle {
        return Bundle().apply {
            putBoolean(KEY_SUCCESS, false)
            putString(KEY_ERROR_MESSAGE, message)
        }
    }
    
    // Required ContentProvider methods
    override fun onCreate(): Boolean = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = "application/vnd.mcp.tool"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
```

### 2. Register the Content Provider in Your Manifest

Add the content provider to your `AndroidManifest.xml`:

```xml
<provider
    android:name=".McpToolProvider"
    android:authorities="com.yourcompany.yourapp.mcptool"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <data android:mimeType="application/vnd.mcp.tool" />
    </intent-filter>
</provider>
```

**Important notes:**
- The `android:exported="true"` attribute is required for the MCP app to access your provider
- The intent filter with the MIME type `application/vnd.mcp.tool` is how the MCP app discovers your tool
- Choose a unique authority to avoid conflicts with other apps

### 3. Tool Protocol Details

#### Tool Info

When the MCP app calls your provider with `get_tool_info`, you need to return:

| Key | Type | Description |
|-----|------|-------------|
| success | Boolean | Whether the request was successful |
| tool_name | String | Name of your tool (will be exposed to AI) |
| tool_description | String | Description of what your tool does |
| tool_input_schema | String | JSON string of parameter definitions |
| tool_input_required | String[] | Array of required parameter names |

#### Tool Execution

When the MCP app calls your provider with `execute_tool`, it will provide:

| Key | Type | Description |
|-----|------|-------------|
| tool_name | String | Name of the tool to execute |
| tool_arguments | Map<String, String> | Arguments provided by the AI |

Your provider should return:

| Key | Type | Description |
|-----|------|-------------|
| success | Boolean | Whether execution was successful |
| tool_result | String | Result text (on success) |
| error_message | String | Error message (on failure) |

## Best Practices

1. **Descriptive Tool Names**: Use clear, descriptive names for your tools
2. **Detailed Descriptions**: Provide detailed descriptions so AI models know when to use your tool
3. **Input Validation**: Always validate inputs to ensure they match your schema
4. **Error Handling**: Return meaningful error messages when things go wrong
5. **Security Considerations**: Remember that any app can call your content provider

## Example Tool Implementations

### Weather Forecast Tool

```kotlin
// In getToolInfo()
Bundle().apply {
    putBoolean(KEY_SUCCESS, true)
    putString(KEY_TOOL_NAME, "get_weather_forecast")
    putString(KEY_TOOL_DESCRIPTION, "Gets weather forecast for a location")
    putString(KEY_TOOL_INPUT_SCHEMA, """
        {
            "location": {"type": "string", "description": "City name or coordinates"},
            "days": {"type": "number", "description": "Number of days (1-7)"}
        }
    """.trimIndent())
    putStringArray(KEY_TOOL_INPUT_REQUIRED, arrayOf("location"))
}

// In executeToolRequest()
val location = arguments["location"] ?: return errorBundle("Location required")
val days = arguments["days"]?.toIntOrNull() ?: 1

// Call your weather API here
val forecast = weatherService.getForecast(location, days)
return Bundle().apply {
    putBoolean(KEY_SUCCESS, true)
    putString(KEY_TOOL_RESULT, forecast.toString())
}
```

## Troubleshooting

- **Tool Not Discovered**: Verify your manifest has the correct intent filter and MIME type
- **Permission Errors**: Make sure your content provider is exported
- **Execution Failures**: Check that your parameter handling matches your declared schema

## Need Help?

If you have any questions about integrating with the MCP server, please reach out to our developer support.
