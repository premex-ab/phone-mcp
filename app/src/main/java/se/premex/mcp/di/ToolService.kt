package se.premex.mcp.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.premex.mcp.core.tool.McpTool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for managing MCP tools and their enabled state
 */
@Singleton
class ToolService @Inject constructor(
    private val availableTools: Set<@JvmSuppressWildcards McpTool>
) {
    private val _toolEnabledStates = MutableStateFlow(
        availableTools.associate { it.id to it.enabledByDefault }
    )

    /**
     * The current enabled state of all tools
     */
    val toolEnabledStates: StateFlow<Map<String, Boolean>> = _toolEnabledStates.asStateFlow()

    /**
     * Get all available tools
     */
    val tools: Set<McpTool> get() = availableTools

    /**
     * Toggle the enabled state of a tool
     */
    fun toggleToolEnabled(toolId: String) {
        val currentStates = _toolEnabledStates.value.toMutableMap()
        currentStates[toolId]?.let { isEnabled ->
            currentStates[toolId] = !isEnabled
            _toolEnabledStates.value = currentStates
        }
    }

    /**
     * Check if a tool is enabled
     */
    fun isToolEnabled(toolId: String): Boolean {
        return _toolEnabledStates.value[toolId] ?: false
    }

    /**
     * Get a tool by its ID
     */
    fun getToolById(toolId: String): McpTool? {
        return availableTools.find { it.id == toolId }
    }
}
