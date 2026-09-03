package se.premex.mcp.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.premex.mcp.AppFunctionStateSynchronizer
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
    private val toolPreferencesRepository: ToolPreferencesRepository,
    private val appFunctionStateSynchronizer: AppFunctionStateSynchronizer,
    @AppCoroutineScope private val appScope: CoroutineScope,
) {
    private val _toolEnabledStates = MutableStateFlow(
        availableTools.associate { it.id to it.enabledByDefault }
    )

    /**
     * The current enabled state of all tools
     */
    val toolEnabledStates: StateFlow<Map<String, Boolean>> = _toolEnabledStates.asStateFlow()

    init {
        // Load saved tool states before observing changes so defaults cannot
        // overwrite persisted choices during application startup.
        appScope.launch {
            val savedToolStates = toolPreferencesRepository.getToolEnabledStates().first()
            _toolEnabledStates.value = mergeSavedToolStates(
                availableTools = availableTools,
                defaultStates = _toolEnabledStates.value,
                savedStates = savedToolStates,
            )
            appScope.launch {
                toolEnabledStates.collect { states ->
                    toolPreferencesRepository.updateAllToolStates(states)
                }
            }
            appScope.launch {
                toolEnabledStates.collectLatest { states ->
                    appFunctionStateSynchronizer.synchronize(availableTools, states)
                }
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

internal fun mergeSavedToolStates(
    availableTools: Set<McpTool>,
    defaultStates: Map<String, Boolean>,
    savedStates: Map<String, Boolean>,
): Map<String, Boolean> {
    val availableToolIds = availableTools.mapTo(mutableSetOf()) { it.id }
    val mergedStates = defaultStates.toMutableMap()
    savedStates.forEach { (toolId, isEnabled) ->
        if (toolId in availableToolIds) {
            mergedStates[toolId] = isEnabled
        }
    }

    // Before the SMS tools had distinct IDs, both rows were persisted as "sms".
    // Preserve that choice for the newly independent SMS-draft tool.
    if (SMS_INTENT_TOOL_ID in availableToolIds && SMS_INTENT_TOOL_ID !in savedStates) {
        savedStates[LEGACY_SMS_TOOL_ID]?.let { wasEnabled ->
            mergedStates[SMS_INTENT_TOOL_ID] = wasEnabled
        }
    }
    return mergedStates
}

private const val LEGACY_SMS_TOOL_ID = "sms"
private const val SMS_INTENT_TOOL_ID = "sms_intent"
