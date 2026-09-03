package se.premex.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import org.junit.Assert.assertEquals
import org.junit.Test
import se.premex.mcp.core.tool.McpTool

class AppFunctionStateSynchronizerTest {
    @Test
    fun desiredAppFunctionStates_followsEachToolSwitch() {
        val enabledTool = fakeTool("camera", setOf("takePhoto"))
        val disabledTool = fakeTool("files", setOf("listFiles", "readFile"))

        assertEquals(
            mapOf(
                "takePhoto" to true,
                "listFiles" to false,
                "readFile" to false,
            ),
            desiredAppFunctionStates(
                setOf(enabledTool, disabledTool),
                mapOf("camera" to true, "files" to false),
            ),
        )
    }

    @Test
    fun desiredAppFunctionStates_ignoresToolsWithoutPublishedFunctions() {
        assertEquals(
            emptyMap<String, Boolean>(),
            desiredAppFunctionStates(
                setOf(fakeTool("external_tools", emptySet())),
                mapOf("external_tools" to true),
            ),
        )
    }

    private fun fakeTool(id: String, functionIds: Set<String>) = object : McpTool {
        override val id: String = id
        override val name: String = id
        override val enabledByDefault: Boolean = false
        override val appFunctionIds: Set<String> = functionIds
        override val disclaim: String? = null
        override fun configure(server: Server) = Unit
        override fun requiredPermissions(): Set<String> = emptySet()
    }
}
