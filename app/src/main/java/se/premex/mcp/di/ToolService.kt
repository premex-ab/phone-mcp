package se.premex.mcp.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.premex.mcp.core.tool.McpTool
import se.premex.mcp.data.ToolPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for managing MCP tools and their enabled state
 */
@Singleton
class ToolService @Inject constructor(
    private val availableTools: Set<@JvmSuppressWildcards McpTool>,
    private val toolPreferencesRepository: ToolPreferencesRepository
) {
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val _toolEnabledStates = MutableStateFlow(
        availableTools.associate { it.id to it.enabledByDefault }
    )

    /**
     * The current enabled state of all tools
     */
    val toolEnabledStates: StateFlow<Map<String, Boolean>> = _toolEnabledStates.asStateFlow()

    init {
        // Load saved tool states from DataStore
        serviceScope.launch {
            val savedToolStates = toolPreferencesRepository.getToolEnabledStates().first()

            // Merge saved states with defaults for any new tools
            val mergedStates = _toolEnabledStates.value.toMutableMap()
            savedToolStates.forEach { (toolId, isEnabled) ->
                // Only use saved state if the tool still exists
                if (availableTools.any { it.id == toolId }) {
                    mergedStates[toolId] = isEnabled
                }
            }

            _toolEnabledStates.value = mergedStates
        }

        // Set up collection of tool state changes to persist them
        serviceScope.launch {
            toolEnabledStates.collect { states ->
                toolPreferencesRepository.updateAllToolStates(states)
            }
        }
    }

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
