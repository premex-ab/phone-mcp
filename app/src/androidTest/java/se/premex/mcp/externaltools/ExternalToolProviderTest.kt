package se.premex.mcp.externaltools

import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalToolProviderTest {

    companion object {
        const val METHOD_GET_TOOL_INFO = "get_tool_info"

        // Bundle keys from MCP Tool protocol
        const val KEY_TOOL_NAME = "tool_name"
        const val KEY_TOOL_DESCRIPTION = "tool_description"
        const val KEY_TOOL_INPUT_SCHEMA = "tool_input_schema"
        const val KEY_TOOL_INPUT_REQUIRED = "tool_input_required"
        const val KEY_SUCCESS = "success"
        const val KEY_ERROR_MESSAGE = "error_message"

        // External provider action
        const val MCP_PROVIDER_ACTION = "se.premex.mcp.MCP_PROVIDER"

        // Expected values for the CalculatorToolProvider
        const val EXPECTED_CALCULATOR_AUTHORITY = "se.premex.externalmcptool.calculator"
        const val EXPECTED_TOOL_NAME = "calculator"
        const val EXPECTED_MIME_TYPE = "application/vnd.mcp.tool"
    }

    @Test
    fun testDiscoverAndQueryCalculatorToolProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Step 1: Discover the content provider
        val providers = discoverMcpToolProviders(context)

        // There should be at least one provider (the calculator tool)
        Assert.assertTrue("No MCP tool providers found", providers.isNotEmpty())

        // Find the calculator provider by authority
        val calculatorProvider = providers.find { it.authority == EXPECTED_CALCULATOR_AUTHORITY }
        Assert.assertNotNull("Calculator provider not found", calculatorProvider)

        // Step 2: Query the tool information from the calculator provider
        val toolInfo = queryToolInfo(context, calculatorProvider!!.authority)

        // Step 3: Validate the configuration settings
        Assert.assertTrue("Query was not successful", toolInfo.getBoolean(KEY_SUCCESS))
        Assert.assertEquals("Incorrect tool name", EXPECTED_TOOL_NAME, toolInfo.getString(KEY_TOOL_NAME))

        // Validate tool description is present
        Assert.assertNotNull("Tool description should be present", toolInfo.getString(KEY_TOOL_DESCRIPTION))

        // Validate input schema
        val inputSchemaStr = toolInfo.getString(KEY_TOOL_INPUT_SCHEMA)!!
        val inputSchema = JSONObject(inputSchemaStr)
        Assert.assertTrue("Input schema should contain 'operation' parameter", inputSchema.has("operation"))
        Assert.assertTrue("Input schema should contain 'a' parameter", inputSchema.has("a"))
        Assert.assertTrue("Input schema should contain 'b' parameter", inputSchema.has("b"))

        // Validate required inputs
        val requiredInputs = toolInfo.getStringArray(KEY_TOOL_INPUT_REQUIRED)
        Assert.assertNotNull("Required inputs should be present", requiredInputs)
        Assert.assertEquals("Expected 3 required inputs", 3, requiredInputs?.size)
        Assert.assertTrue("'operation' should be required", requiredInputs?.contains("operation") == true)
        Assert.assertTrue("'a' should be required", requiredInputs?.contains("a") == true)
        Assert.assertTrue("'b' should be required", requiredInputs?.contains("b") == true)
    }

    /**
     * Discovers all MCP tool providers installed on the device
     */
    private fun discoverMcpToolProviders(context: Context): List<ProviderInfo> {
        val packageManager = context.packageManager
        val intent = Intent(MCP_PROVIDER_ACTION)

        val resolveInfoList = packageManager.queryIntentContentProviders(intent, PackageManager.MATCH_ALL)

        return resolveInfoList.map { resolveInfo ->
            val providerInfo = resolveInfo.providerInfo
            ProviderInfo(
                name = providerInfo.name,
                packageName = providerInfo.packageName,
                authority = providerInfo.authority
            )
        }
    }

    /**
     * Queries a tool provider for its information
     */
    private fun queryToolInfo(context: Context, authority: String): Bundle {
        val uri = Uri.parse("content://$authority")
        val client = context.contentResolver.acquireContentProviderClient(uri)
            ?: throw IllegalStateException("Could not acquire content provider client for $authority")

        try {
            val result = client.call(METHOD_GET_TOOL_INFO, null, null)
            return result ?: throw IllegalStateException("Null result from provider $authority")
        } finally {
            client.close()
        }
    }

    /**
     * Helper class to store provider information
     */
    data class ProviderInfo(
        val name: String,
        val packageName: String,
        val authority: String
    )
}
