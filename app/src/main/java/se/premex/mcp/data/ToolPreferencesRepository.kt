package se.premex.mcp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.toolPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tool_preferences"
)

/**
 * Repository for managing tool preferences with DataStore
 */
@Singleton
class ToolPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.toolPreferencesDataStore

    /**
     * Get the saved tool enabled states as a Flow
     */
    fun getToolEnabledStates(): Flow<Map<String, Boolean>> {
        return dataStore.data.map { preferences ->
            preferences.asMap().filter { it.key.name.startsWith(TOOL_ENABLED_PREFIX) }
                .mapKeys { it.key.name.removePrefix(TOOL_ENABLED_PREFIX) }
                .mapValues { (it.value as? Boolean) ?: false }
        }
    }

    /**
     * Update a tool's enabled state
     */
    suspend fun updateToolEnabledState(toolId: String, isEnabled: Boolean) {
        val key = toolEnabledKey(toolId)
        dataStore.edit { preferences ->
            preferences[key] = isEnabled
        }
    }

    /**
     * Update multiple tool states at once
     */
    suspend fun updateAllToolStates(states: Map<String, Boolean>) {
        dataStore.edit { preferences ->
            states.forEach { (toolId, isEnabled) ->
                preferences[toolEnabledKey(toolId)] = isEnabled
            }
        }
    }

    /**
     * Create a preference key for a tool's enabled state
     */
    private fun toolEnabledKey(toolId: String) =
        booleanPreferencesKey("$TOOL_ENABLED_PREFIX$toolId")

    companion object {
        private const val TOOL_ENABLED_PREFIX = "tool_enabled_"
    }
}
