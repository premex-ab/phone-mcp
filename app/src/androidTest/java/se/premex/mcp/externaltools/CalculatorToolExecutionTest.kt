package se.premex.mcp.externaltools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Serializable

@RunWith(AndroidJUnit4::class)
class CalculatorToolExecutionTest {

    companion object {
        const val METHOD_EXECUTE_TOOL = "execute_tool"

        // Bundle keys from MCP Tool protocol
        const val KEY_TOOL_NAME = "tool_name"
        const val KEY_TOOL_ARGUMENTS = "tool_arguments"
        const val KEY_TOOL_RESULT = "tool_result"
        const val KEY_SUCCESS = "success"
        const val KEY_ERROR_MESSAGE = "error_message"

        // External provider action
        const val MCP_PROVIDER_ACTION = "se.premex.mcp.MCP_PROVIDER"

        // Expected values for the CalculatorToolProvider
        const val EXPECTED_CALCULATOR_AUTHORITY = "se.premex.externalmcptool.calculator"
        const val EXPECTED_TOOL_NAME = "calculator"
    }

    @Test
    fun testExecuteCalculatorToolAddition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Step 1: Find the calculator provider
        val calculatorAuthority = EXPECTED_CALCULATOR_AUTHORITY

        // Create the arguments for the addition operation
        val arguments = mapOf(
            "operation" to "add",
            "a" to 5.0,
            "b" to 3.0
        )

        // Step 2: Execute the calculator tool
        val result = executeToolOperation(context, calculatorAuthority, EXPECTED_TOOL_NAME, arguments)

        // Step 3: Validate the calculation result
        Assert.assertTrue("Calculator operation failed", result.getBoolean(KEY_SUCCESS))

        // Extract and parse the result JSON
        val resultJson = JSONObject(result.getString(KEY_TOOL_RESULT))

        // Verify the calculation was correct (5 + 3 = 8)
        val calculationResult = resultJson.getDouble("result")
        Assert.assertEquals("Addition calculation incorrect", 8.0, calculationResult, 0.0001)

        // Verify that the operation and operands were correctly reflected in the result
        Assert.assertEquals("Operation mismatch", "add", resultJson.getString("operation"))
        Assert.assertEquals("First operand mismatch", 5.0, resultJson.getDouble("a"), 0.0001)
        Assert.assertEquals("Second operand mismatch", 3.0, resultJson.getDouble("b"), 0.0001)
    }

    @Test
    fun testExecuteCalculatorToolMultipleOperations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Test case data: operation, a, b, expected result
        val testCases = listOf(
            CalculatorTestCase("add", 10.0, 5.0, 15.0),
            CalculatorTestCase("subtract", 10.0, 5.0, 5.0),
            CalculatorTestCase("multiply", 10.0, 5.0, 50.0),
            CalculatorTestCase("divide", 10.0, 5.0, 2.0),
            CalculatorTestCase("power", 2.0, 3.0, 8.0)
        )

        for (testCase in testCases) {
            val arguments = mapOf(
                "operation" to testCase.operation,
                "a" to testCase.a,
                "b" to testCase.b
            )

            // Execute the calculator tool for each test case
            val result = executeToolOperation(context, EXPECTED_CALCULATOR_AUTHORITY, EXPECTED_TOOL_NAME, arguments)

            // Validate the calculation result
            Assert.assertTrue("Calculator operation ${testCase.operation} failed", result.getBoolean(KEY_SUCCESS))

            // Extract and verify the calculation result
            val resultJson = JSONObject(result.getString(KEY_TOOL_RESULT))
            val calculationResult = resultJson.getDouble("result")

            // Verify the calculation was correct
            Assert.assertEquals(
                "${testCase.operation} calculation incorrect",
                testCase.expected,
                calculationResult,
                0.0001
            )
        }
    }

    @Test
    fun testCalculatorErrorHandling() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Test division by zero which should return an error
        val arguments = mapOf(
            "operation" to "divide",
            "a" to 10.0,
            "b" to 0.0
        )

        val result = executeToolOperation(context, EXPECTED_CALCULATOR_AUTHORITY, EXPECTED_TOOL_NAME, arguments)

        // Verify that the operation failed and returned an error message
        Assert.assertFalse("Operation should have failed", result.getBoolean(KEY_SUCCESS))

        // Check that there's an error message about division by zero
        val errorMessage = result.getString(KEY_ERROR_MESSAGE)
        Assert.assertNotNull("Error message should be present", errorMessage)
        Assert.assertTrue("Error should mention division by zero",
            errorMessage?.contains("division by zero", ignoreCase = true) == true)
    }

    /**
     * Executes a tool operation by calling the content provider
     */
    private fun executeToolOperation(
        context: Context,
        authority: String,
        toolName: String,
        arguments: Map<String, Any>
    ): Bundle {
        val uri = Uri.parse("content://$authority")
        val client = context.contentResolver.acquireContentProviderClient(uri)
            ?: throw IllegalStateException("Could not acquire content provider client for $authority")

        try {
            val extras = Bundle().apply {
                putString(KEY_TOOL_NAME, toolName)
                putSerializable(KEY_TOOL_ARGUMENTS, arguments as Serializable)
            }

            val result = client.call(METHOD_EXECUTE_TOOL, null, extras)
            return result ?: throw IllegalStateException("Null result from provider $authority")
        } finally {
            client.close()
        }
    }

    /**
     * Helper class for calculator test cases
     */
    data class CalculatorTestCase(
        val operation: String,
        val a: Double,
        val b: Double,
        val expected: Double
    )
}
