package se.premex.mcp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import se.premex.mcp.remote.ActivationStep
import se.premex.mcp.ui.ActivationGuide
import se.premex.mcp.ui.McpClient
import se.premex.mcp.ui.theme.MCPServerTheme

class ActivationGuideTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun disconnectedReturningUserGetsReconnectHelp() {
        compose.setContent { MCPServerTheme {
            ActivationGuide(ActivationStep.CONNECT, true, true, McpClient.GEMINI_CLI, {}, {}, {})
        } }
        compose.onNodeWithText(context.getString(R.string.activation_reconnect_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.activation_success)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.copy_prompt)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.activation_open_guide, context.getString(R.string.client_gemini_cli))).assertIsDisplayed()
    }

    @Test fun sensorOptInPrecedesTheCopyableFirstPrompt() {
        val enabled = mutableStateOf(false)
        var copies = 0
        compose.setContent { MCPServerTheme {
            ActivationGuide(ActivationStep.from(true, true, false, enabled.value), false, enabled.value,
                McpClient.CODEX, { enabled.value = true }, { copies++ }, {})
        } }
        compose.onNodeWithText(context.getString(R.string.copy_prompt)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.activation_enable_sensor)).performClick()
        compose.onNodeWithText(context.getString(R.string.copy_prompt)).performClick()
        compose.runOnIdle { assertEquals(1, copies) }
    }

    @Test fun successfulUserGetsTheFileTaskWithoutReenablingSensors() {
        compose.setContent { MCPServerTheme {
            ActivationGuide(ActivationStep.READY, true, false, McpClient.CLAUDE_CODE, {}, {}, {})
        } }
        compose.onNodeWithText(context.getString(R.string.activation_success)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.activation_next_prompt)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.activation_enable_sensor)).assertDoesNotExist()
    }
}
