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
import se.premex.mcp.provider.ToolInfo
import se.premex.mcp.provider.ToolInput
import se.premex.mcp.provider.Tools

@RunWith(AndroidJUnit4::class)
class ExternalToolProviderTest {

    companion object {
        const val METHOD_GET_TOOL_INFO = "get_tool_info"

        // Bundle keys from MCP Tool protocol
        const val KEY_TOOL = "tool"
        const val KEY_SUCCESS = "success"

        // External provider action
        const val MCP_PROVIDER_ACTION = "se.premex.mcp.MCP_PROVIDER"

        // Expected values for the CalculatorToolProvider
        const val EXPECTED_CALCULATOR_AUTHORITY = "se.premex.externalmcptool.authorities.McpProvider"
        const val EXPECTED_TOOL_NAME = "calculator"
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


        val toolJson = toolInfo.getString(KEY_TOOL)!!
        val tools = Json.decodeFromString<Tools>(toolJson)
        val tool = tools.tools[EXPECTED_TOOL_NAME]!!

        Assert.assertNotNull(tool)

        // Validate tool description is present
        Assert.assertNotNull("Tool description should be present", tool.description)

        // Validate input schema
        val inputSchemaStr = tool.inputs
        val expectation = listOf(
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

        Assert.assertEquals("inputSchemaStr should match expectation", inputSchemaStr, expectation)
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
