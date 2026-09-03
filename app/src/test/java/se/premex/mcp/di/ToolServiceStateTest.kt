package se.premex.mcp.di

import io.modelcontextprotocol.kotlin.sdk.server.Server
import org.junit.Assert.assertEquals
import org.junit.Test
import se.premex.mcp.core.tool.McpTool

class ToolServiceStateTest {
    @Test
    fun mergeSavedToolStates_migratesLegacySmsChoiceToSmsIntent() {
        val directSms = fakeTool("sms")
        val smsIntent = fakeTool("sms_intent")

        assertEquals(
            mapOf("sms" to true, "sms_intent" to true),
            mergeSavedToolStates(
                availableTools = setOf(directSms, smsIntent),
                defaultStates = mapOf("sms" to false, "sms_intent" to false),
                savedStates = mapOf("sms" to true),
            ),
        )
    }

    @Test
    fun mergeSavedToolStates_preservesIndependentSmsIntentChoice() {
        val directSms = fakeTool("sms")
        val smsIntent = fakeTool("sms_intent")

        assertEquals(
            mapOf("sms" to true, "sms_intent" to false),
            mergeSavedToolStates(
                availableTools = setOf(directSms, smsIntent),
                defaultStates = mapOf("sms" to false, "sms_intent" to false),
                savedStates = mapOf("sms" to true, "sms_intent" to false),
            ),
        )
    }

    private fun fakeTool(id: String) = object : McpTool {
        override val id = id
        override val name = id
        override val enabledByDefault = false
        override val disclaim: String? = null
        override fun configure(server: Server) = Unit
        override fun requiredPermissions(): Set<String> = emptySet()
    }
}
